package fr.inria.edelweiss.kgraph.core;

import fr.inria.acacia.corese.api.IDatatype;
import fr.inria.acacia.corese.cg.datatype.DatatypeMap;
import fr.inria.acacia.corese.exceptions.CoreseDatatypeException;
import java.util.Iterator;
import java.util.List;

import fr.inria.edelweiss.kgram.api.core.Edge;
import fr.inria.edelweiss.kgram.api.core.Entity;
import fr.inria.edelweiss.kgram.api.core.ExprType;
import fr.inria.edelweiss.kgram.api.core.Node;
import fr.inria.edelweiss.kgram.tool.MetaIterator;
import fr.inria.edelweiss.kgraph.core.edge.EdgeGeneric;
import java.util.ArrayList;

/**
 * Transient Dataset over graph in order to iterate edges 
 * Use case: Producer getEdges()
 * default or named graphs, from or from named
 * Default graph: eliminate duplicate edges during iteration
 * May take edge level into account for RuleEngine optimization
 * 
 */
public class DataProducer implements Iterable<Entity>, Iterator<Entity> {

    static final List<Entity> empty     = new ArrayList<Entity>(0);
    static final List<Node> emptyNode   = new ArrayList<Node>(0);
    Iterable<Entity> iter;
    Iterator<Entity> it;
    EdgeGeneric glast;
    Edge last;
    List<Node> from;
    Node fromNode;
    IDatatype value;
    Graph graph;
    boolean isNamedGraph, hasFrom, hasOneFrom, isMember;
    int test;
    private int level = -1;
    boolean hasLevel = false;

    DataProducer(Graph g) {
        graph = g;
        isNamedGraph = false;
        isMember = true;
        hasFrom = false;
        hasOneFrom = false;
        test = -1;
    }

    public static DataProducer create(Graph g) {
        DataProducer ei = new DataProducer(g);
        return ei;
    }

    @Deprecated
    public static DataProducer create(Graph g, Iterable<Entity> i) {
        DataProducer ei = new DataProducer(g);
        ei.setIterable(i);
        return ei;
    }
    
    @Deprecated
     public DataProducer(Graph g, List<Node> list) {
        this(g);
        from(list);
    }

    @Deprecated 
    public DataProducer(Graph g, Iterable<Entity> i, List<Node> list) {
        this(g, list);
        setIterable(i);
    }
       
    public Iterable<Entity> iterate() {
        return iterate(graph.getTopProperty());
    }

    public Iterable<Entity> iterate(Node predicate) {
        setIterable(graph.getEdges(predicate));
        return this;
    }

    public Iterable<Entity> iterate(Node predicate, Node node) {
        return iterate(predicate, node, 0);
    }
    
    public Iterable<Entity> iterate(Node node, int n) {
        return iterate(graph.getTopProperty(), node, n);
    }

    public Iterable<Entity> iterate(Node predicate, Node node, int n) {
        // optimize special cases
        if (isNamedGraph) {
            if (node == null && hasFrom && !from.isEmpty()) {
                // exploit IGraph Index to focus on from
                return getEdgesFromNamed(from, predicate);
            } else if (!hasFrom) {
                // iterate all named graph edges
                return graph.properGetEdges(predicate, node, n);
            }
        } 
        // default graph
        else if (node == null && hasFrom && !from.isEmpty()) {
            // no query node has a value, there is a from           
            if (!isFromOK(from)) {
                // from URIs are unknown in current graph
                return empty;
            }
        } 
        else if (!hasFrom && graph.nbGraphNodes() == 1 && !hasLevel) {
            return graph.properGetEdges(predicate, node, n);
        }

        // general case
        setIterable(graph.properGetEdges(predicate, node, n));
        return this;
    }

    /**
     * Iterate predicate in from named
     */
    Iterable<Entity> getEdgesFromNamed(List<Node> from, Node predicate) {
        MetaIterator<Entity> meta = new MetaIterator<Entity>();

        for (Node src : from) {
            Node tfrom = graph.getGraphNode(src.getLabel());
            if (tfrom != null) {
                Iterable<Entity> it = graph.getEdges(predicate, tfrom, Graph.IGRAPH);
                if (it != null) {
                    meta.next(it);
                }
            }
        }

        if (meta.isEmpty()) {
            return empty;
        } else {
            return meta;
        }
    }
    
     boolean isFromOK(List<Node> from) {
        for (Node node : from) {
            Node tfrom = graph.getNode(node);
            if (tfrom != null && graph.isGraphNode(tfrom)) {
                return true;
            }
        }
        return false;
    }
    
    public void setIterable(Iterable<Entity> it){
        iter = it;
    }
    
    public DataProducer level(int n){
        setLevel(n);
        return  this;
    }
    
    public DataProducer named(){
        this.isNamedGraph = true;
        return this;
    }
    
    public DataProducer from(List<Node> list) {
        from = list;
        hasFrom = from.size() > 0;
        hasOneFrom = from.size() == 1;
        if (hasOneFrom) {
            fromNode = graph.getGraphNode(from.get(0).getLabel());
            if (fromNode == null){
                hasOneFrom = false;
            }
        }
        return this;
    }
    
    public DataProducer from(Node g) {
        if (g != null) {
            fromNode = g;
            hasFrom = true;
            hasOneFrom = true;
            if (from == null){
                from = emptyNode;
            }
        }
        return this;
    }
    
    
    public DataProducer from(List<Node> list, Node source) {
        if (source == null){
            return from(list);
        }
        return from(source);
    }
    
    /**
     * The from clause is taken as skip from
     * the graphs are skipped to answer query
     */    
    public DataProducer minus(List<Node> list){
        from(list);
        isMember = false;
        return this;
    }
    
    public DataProducer minus(Node node){
        from(node);
        isMember = false;
        return this;
    }
    
    public DataProducer filter(int test, IDatatype dt){
        this.test = test;
        this.value = dt;
        return this;
    }
    
    public DataProducer filter(int test, int value){
         return filter(test, DatatypeMap.newInstance(value));
    }
    
    public DataProducer filter(int test, double value){
         return filter(test, DatatypeMap.newInstance(value));
    }

    public DataProducer filter(int test, String value){
         return filter(test, DatatypeMap.newInstance(value));
    }

    public DataProducer greaterEqual(IDatatype dt){
        return filter(ExprType.GE, dt);      
    }
    
    public DataProducer lessEqual(IDatatype dt){
        return filter(ExprType.LE, dt);      
    }

    @Override
    public Iterator<Entity> iterator() {
        if (hasOneFrom && fromNode == null) {
            return empty.iterator();
        }
        it = iter.iterator();
        last = null;
        glast = null;
        return this;
    }

    @Override
    public boolean hasNext() {
        return it.hasNext();
    }

    boolean same(Node n1, Node n2) {
        return n1.getIndex() == n2.getIndex();
    }

    @Override
    public Entity next() {

        while (hasNext()) {
            Entity ent = it.next();
            boolean ok = true;

            if (isNamedGraph) {
                // ok
            } 
            else if (last != null) {
                // eliminate successive duplicates
                ok = different(last, ent.getEdge());
            }

            if (ok && hasFrom) {
                ok = result(isFrom(ent));
            }
            
            if (ok && test != -1){
                ok = test(ent);
            }

            if (ok) {
                record(ent);
                if (hasLevel && last.getIndex() < level) {
                    // use case: Rule Engine requires edges with level >= this.level
                    it = empty.iterator();
                    return null;
                }
                return ent;
            }
        }
        return null;
    }
      
    
    boolean different(Edge last, Edge edge) {
        if (edge.getEdgeNode() == null || ! same(last.getEdgeNode(), edge.getEdgeNode())) {
            // different properties: ok
            return true;
        } else {
            int size = last.nbNode();
            if (size == edge.nbNode()) {               
                for (int i = 0; i < size; i++) {
                    if (!same(last.getNode(i), edge.getNode(i))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    
    boolean test(Entity ent) {
        IDatatype dt = (IDatatype)ent.getNode(1).getValue();
        try {
            switch (test){           
                case ExprType.GT: return dt.greater(value);
                case ExprType.GE: return dt.greaterOrEqual(value);
                case ExprType.LE: return dt.lessOrEqual(value);
                case ExprType.LT: return dt.less(value);
                case ExprType.EQ: return dt.equals(value);
                case ExprType.NEQ: return ! dt.equals(value);

                default: return true;
            }
        }
        catch (CoreseDatatypeException e){
            return false;
        }
    }

    void record(Entity ent) {
        if (EdgeIndexer.test){
            record2(ent);
        }
        else {
            last = ent.getEdge();
        }
    }

    // record a copy of ent for last
    void record2(Entity ent) {
        if (glast == null) {
            glast = new EdgeGeneric();
            last = glast;
        }
        glast.duplicate(ent);
    }

    @Override
    public void remove() {
    }

    /**
     *
     * Check if entity graph node is member of from by dichotomy
     */
    boolean isFrom(Entity ent) {
        if (hasOneFrom) {
            return same(fromNode, ent.getGraph());
        } else {
            Node g = ent.getGraph();
            int res = find(from, g);
            return res != -1;
        }
    }
    
    /**
     * isMember = false means skip graph in from clause
     */
    boolean result(boolean found){
        if (isMember){
            return found;
        }
        return ! found;
    }

    public boolean isFrom(List<Node> from, Node node) {
        int res = find(from, node);
        return res != -1;
    }

    int find(List<Node> list, Node node) {
        int res = find(list, node, 0, list.size());
        if (res >= 0 && res < list.size()
                && list.get(res).same(node)) {
            return res;
        }
        return -1;
    }

    /**
     * Find the index of node in list of Node by dichotomy
     */
    int find(List<Node> list, Node node, int first, int last) {
        if (first >= last) {
            return first;
        } else {
            int mid = (first + last) / 2;
            int res = list.get(mid).compare(node);
            if (res >= 0) {
                return find(list, node, first, mid);
            } else {
                return find(list, node, mid + 1, last);
            }
        }
    }

    /**
     * @return the level
     */
    public int getLevel() {
        return level;
    }

    /**
     * @param level the level to set
     */
    public void setLevel(int level) {
        this.level = level;
        hasLevel = level != -1;
    }

}