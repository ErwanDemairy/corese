/*
 * Copyright Inria 2016. 
 */
package fr.inria.corese.rdftograph;

import fr.inria.corese.rdftograph.driver.GdbDriver;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import org.openrdf.model.BNode;
import org.openrdf.model.IRI;
import org.openrdf.model.Literal;
import org.openrdf.model.Model;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.Value;
import org.openrdf.rio.ParserConfig;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFParser;
import org.openrdf.rio.Rio;
import org.openrdf.rio.helpers.AbstractRDFHandler;
import org.openrdf.rio.helpers.BasicParserSettings;
import org.openrdf.rio.helpers.StatementCollector;

import org.openrdf.rio.helpers.NTriplesParserSettings;

/**
 * This application: (i) read a RDF file using sesame library; (ii) write the
 * content into a neo4j DB using the neo4j library.
 *
 * @author edemairy
 */
public class RdfToGraph {

	public enum DbDriver {
		NEO4J, ORIENTDB, TITANDB
	}
	public static final String LITERAL = "literal";
	public static final String IRI = "IRI";
	public static final String BNODE = "bnode";
	public static final String KIND = "kind";
	public static final String LANG = "lang";
	public static final String TYPE = "type";
	public static final String EDGE_G = "g_value";
	public static final String EDGE_P = "p_value";
	public static final String EDGE_S = "s_value";
	public static final String EDGE_O = "o_value";
	public static final String VERTEX_VALUE = "v_value";
	public static final String RDF_EDGE_LABEL = "rdf_edge";
	public static final String RDF_VERTEX_LABEL = "rdf_vertex";

	private static Logger LOGGER = Logger.getLogger(RdfToGraph.class.getName());
	protected Model model;
	protected GdbDriver driver;
	private static final Map<DbDriver, String> DRIVER_TO_CLASS;

	static {
		DRIVER_TO_CLASS = new HashMap<>();
		DRIVER_TO_CLASS.put(DbDriver.NEO4J, "fr.inria.corese.rdftograph.driver.Neo4jDriver");
		DRIVER_TO_CLASS.put(DbDriver.ORIENTDB, "fr.inria.corese.rdftograph.driver.OrientDbDriver");
		DRIVER_TO_CLASS.put(DbDriver.TITANDB, "fr.inria.corese.rdftograph.driver.TitanDriver");
	}

	private class StatementCounter extends AbstractRDFHandler {

		private int triples = 0;

		@Override
		public void handleStatement(Statement statement) {
			Resource context = statement.getContext();
			Resource source = statement.getSubject();
			IRI predicat = statement.getPredicate();
			Value object = statement.getObject();

			String contextString = (context == null) ? "" : context.stringValue();

			Object sourceNode = driver.createNode(source);
			Object objectNode = driver.createNode(object);

			Map<String, Object> properties = new HashMap();
			properties.put(EDGE_G, contextString);
			driver.createRelationship(sourceNode, objectNode, predicat.stringValue(), properties);
			triples++;
			if (triples % CHUNK_SIZE == 0) {
				LOGGER.log(Level.INFO, "{0}", triples);
				try {
					driver.commit();
				} catch (Exception ex) {
					LOGGER.log(Level.SEVERE, "Trying to pursue after: {0}", ex.getMessage());
					ex.printStackTrace();
				}
			}
		}
	}

	public RdfToGraph() {
	}

	/**
	 *
	 * @param driver Short name given in DRIVER_TO_CLASS for the driver to
	 * use.
	 */
	public RdfToGraph setDriver(DbDriver driver) {
		try {
			String driverClassName = DRIVER_TO_CLASS.get(driver);
			this.driver = buildDriver(driverClassName);
		} catch (Exception ex) {
			LOGGER.log(Level.SEVERE, "Impossible to set driver to {0}, caused by ", driver);
			ex.printStackTrace();
		}
		return this;
	}

	private static GdbDriver buildDriver(String driverName) throws Exception {
		GdbDriver result;
		Constructor driverConstructor = Class.forName(driverName).getConstructor();
		result = (GdbDriver) driverConstructor.newInstance();
		return result;
	}

	public RdfToGraph convertFileToDb(String rdfFileName, RDFFormat format, String dbPath) throws FileNotFoundException, IOException {
		InputStream inputStream;
		if (rdfFileName.endsWith(".gz")) {
			inputStream = new GZIPInputStream(new BufferedInputStream(new FileInputStream(rdfFileName)));
		} else {
			inputStream = new FileInputStream(new File(rdfFileName));
		}
		convertStreamToDb(inputStream, format, dbPath);
		return this;
	}

	/**
	 * Read a RDF stream and serialize it inside a Neo4j graph.
	 *
	 * @param rdfStream Input stream containing rdf data
	 * @param format Format used for the rdf representation in the input
	 * stream
	 * @param dbPath Where to store the rdf data.
	 */
	public void convertStreamToDb(InputStream rdfStream, RDFFormat format, String dbPath) {
		try {
			LOGGER.info("** begin of convert **");
			LOGGER.log(Level.INFO, "opening the db at {0}", dbPath);
			driver.openDb(dbPath);
			LOGGER.info("Loading file");
			readFile(rdfStream, format);
			LOGGER.info("Writing graph in db");
//			writeModelToNeo4j();
			LOGGER.info("closing DB");
			driver.closeDb();
			LOGGER.info("** end of convert **");
		} catch (IOException ex) {
			LOGGER.log(Level.SEVERE, "Exception during conversion: {0}", ex.toString());
		}
	}

	/**
	 * Fill model with the content of an input stream.
	 *
	 * @param in Stream on an RDF file.
	 * @param format Format used to represent the RDF in the file.
	 * @throws IOException
	 */
	public void readFile_old(InputStream in, RDFFormat format) throws IOException {
		RDFParser rdfParser = Rio.createParser(format);
		ParserConfig config = new ParserConfig();
		config.set(BasicParserSettings.PRESERVE_BNODE_IDS, true);
		config.addNonFatalError(NTriplesParserSettings.FAIL_ON_NTRIPLES_INVALID_LINES);
		rdfParser.setParserConfig(config);
		model = new org.openrdf.model.impl.LinkedHashModel();
		rdfParser.setRDFHandler(new StatementCollector(model));
		rdfParser.parse(in, "");
	}

	public void readFile(InputStream in, RDFFormat format) throws IOException {
		StatementCounter myCounter = new StatementCounter();
		RDFParser rdfParser = Rio.createParser(format);
		ParserConfig config = new ParserConfig();
		config.set(BasicParserSettings.PRESERVE_BNODE_IDS, true);
		config.addNonFatalError(NTriplesParserSettings.FAIL_ON_NTRIPLES_INVALID_LINES);
		rdfParser.setParserConfig(config);
		rdfParser.setRDFHandler(myCounter);
		try {
			rdfParser.parse(in, "");
		} catch (Exception e) {
			LOGGER.severe(e.getMessage());
			e.printStackTrace();
		}
	}

	public RdfToGraph setWipeOnOpen(boolean b) {
		driver.setWipeOnOpen(b);
		return this;
	}

	final static private int CHUNK_SIZE = 50_000; //Integer.MAX_VALUE;

	public void writeModelToNeo4j() {
		int triples = 0;
		for (Statement statement : model) {
			Resource context = statement.getContext();
			Resource source = statement.getSubject();
			IRI predicat = statement.getPredicate();
			Value object = statement.getObject();

			String contextString = (context == null) ? "" : context.stringValue();

			Object sourceNode = driver.createNode(source);
			Object objectNode = driver.createNode(object);

			Map<String, Object> properties = new HashMap();
			properties.put(EDGE_G, contextString);
			driver.createRelationship(sourceNode, objectNode, predicat.stringValue(), properties);
			triples++;
			if (triples % CHUNK_SIZE == 0) {
				LOGGER.info("" + triples);
				driver.commit();
			}
		}
		System.out.println(triples + " processed");
	}

	public static String getKind(Value resource) {
		if (isLiteral(resource)) {
			return LITERAL;
		} else if (isIRI(resource)) {
			return IRI;
		} else if (isBNode(resource)) {
			return BNODE;
		}
		throw new IllegalArgumentException("Impossible to find the type of:" + resource.stringValue());
	}

	private static boolean isType(Class c, Object o) {
		try {
			c.cast(o);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private static boolean isLiteral(Value resource) {
		return isType(Literal.class, resource);
	}

	private static boolean isIRI(Value resource) {
		return isType(IRI.class, resource);
	}

	private static boolean isBNode(Value resource) {
		return isType(BNode.class, resource);
	}

	public static void main(String[] args) throws FileNotFoundException, IOException {
		if (args.length < 2) {
			System.err.println("Usage: rdfToGraph fileName db_path [backend]");
			System.err.println("if the parser cannot guess the format of the input file, NQUADS is used.");
			System.err.print("knwown backends ");
			for (DbDriver driver : DbDriver.values()) {
				System.err.print(driver + " ");
			}
			System.exit(1);
		}
		DbDriver driver = DbDriver.NEO4J;
		if (args.length >= 3) {
			try {
				DbDriver driverParam = DbDriver.valueOf(args[2].toUpperCase());
				driver = driverParam;
			} catch (IllegalArgumentException ex) {
				ex.printStackTrace();
			}
		}
		String rdfFileName = args[0];
		String dbPath = args[1];	
		Optional<RDFFormat> format = Rio.getParserFormatForFileName(rdfFileName);

		RdfToGraph converter = new RdfToGraph().setDriver(driver).convertFileToDb(rdfFileName, format.orElse(RDFFormat.NQUADS), dbPath);
	}
}
