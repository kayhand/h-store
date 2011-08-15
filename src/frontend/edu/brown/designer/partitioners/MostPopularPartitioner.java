/**
 * 
 */
package edu.brown.designer.partitioners;

import java.util.Collection;
import java.util.Observable;
import java.util.Set;

import org.apache.log4j.Logger;
import org.voltdb.catalog.Column;
import org.voltdb.catalog.ProcParameter;
import org.voltdb.catalog.Procedure;
import org.voltdb.catalog.Table;
import org.voltdb.types.PartitionMethodType;

import edu.brown.catalog.CatalogKey;
import edu.brown.catalog.CatalogUtil;
import edu.brown.catalog.special.ReplicatedColumn;
import edu.brown.designer.AccessGraph;
import edu.brown.designer.ColumnSet;
import edu.brown.designer.Designer;
import edu.brown.designer.DesignerEdge;
import edu.brown.designer.DesignerHints;
import edu.brown.designer.DesignerInfo;
import edu.brown.designer.DesignerVertex;
import edu.brown.designer.partitioners.plan.PartitionPlan;
import edu.brown.designer.partitioners.plan.ProcedureEntry;
import edu.brown.designer.partitioners.plan.TableEntry;
import edu.brown.gui.common.GraphVisualizationPanel;
import edu.brown.statistics.Histogram;
import edu.brown.statistics.TableStatistics;
import edu.brown.utils.CollectionUtil;
import edu.brown.utils.EventObserver;
import edu.brown.utils.LoggerUtil;
import edu.brown.utils.StringUtil;
import edu.brown.utils.LoggerUtil.LoggerBoolean;

/**
 * @author pavlo
 */
public class MostPopularPartitioner extends AbstractPartitioner {
    private static final Logger LOG = Logger.getLogger(MostPopularPartitioner.class);
    private final static LoggerBoolean debug = new LoggerBoolean(LOG.isDebugEnabled());
    private final static LoggerBoolean trace = new LoggerBoolean(LOG.isTraceEnabled());
    static {
        LoggerUtil.attachObserver(LOG, debug, trace);
    }
    
    private Long last_memory = null;
    
    /**
     * @param designer
     * @param info
     */
    public MostPopularPartitioner(Designer designer, DesignerInfo info) {
        super(designer, info);
    }
    
    public Long getLastMemory() {
        return last_memory;
    }

    /* (non-Javadoc)
     * @see edu.brown.designer.partitioners.AbstractPartitioner#generate(edu.brown.designer.DesignerHints)
     */
    @Override
    public PartitionPlan generate(DesignerHints hints) throws Exception {
        final PartitionPlan pplan = new PartitionPlan();
        
        // Generate an AccessGraph and select the column with the greatest weight for each table
        final AccessGraph agraph = this.generateAccessGraph();
        final boolean calculate_memory = (hints.force_replication_size_limit != null && hints.max_memory_per_partition != 0);
        
        double total_memory_ratio = 0.0;
        long total_memory_bytes = 0l;
        for (DesignerVertex v : agraph.getVertices()) {
            Table catalog_tbl = v.getCatalogItem();
            String table_key = CatalogKey.createKey(catalog_tbl);
            
            Collection<Column> forced_columns = hints.getForcedTablePartitionCandidates(catalog_tbl); 
            if (trace.get()) LOG.trace("Processing Table " + catalog_tbl.getName());
            
            TableStatistics ts = info.stats.getTableStatistics(catalog_tbl);
            assert(ts != null) : "Null TableStatistics for " + catalog_tbl;
            double size_ratio = (calculate_memory ? (ts.tuple_size_total / (double)hints.max_memory_per_partition) : 0);
            TableEntry pentry = null;
            if (trace.get()) LOG.trace(String.format("MEMORY %-25s%.02f", catalog_tbl.getName() + ": ", size_ratio));
            
            // -------------------------------
            // Replication
            // -------------------------------
            if (hints.force_replication.contains(table_key) ||
                (calculate_memory && ts.readonly && size_ratio <= hints.force_replication_size_limit)) {
                total_memory_ratio += size_ratio;
                total_memory_bytes += ts.tuple_size_total;
                if (debug.get()) LOG.debug(String.format("PARTITION %-25s%s", catalog_tbl.getName(), ReplicatedColumn.COLUMN_NAME));
                pentry = new TableEntry(PartitionMethodType.REPLICATION, null, null, null);
            
            // -------------------------------
            // Forced Partitioning
            // -------------------------------
            } else if (forced_columns.isEmpty() == false) {
                // Assume there is only one candidate
                assert(forced_columns.size() == 1) : "Unexpected number of forced columns: " + forced_columns;
                Column catalog_col = CollectionUtil.first(forced_columns);
                pentry = new TableEntry(PartitionMethodType.HASH, catalog_col, null, null);
                if (debug.get()) LOG.debug("FORCED PARTITION: " + CatalogUtil.getDisplayName(catalog_col));
                total_memory_ratio += (size_ratio / (double)info.getNumPartitions());
                total_memory_bytes += (ts.tuple_size_total / (double)info.getNumPartitions());

            // -------------------------------
            // Select Most Popular
            // -------------------------------
            } else {
                // If there are no edges, then we'll just randomly pick a column since it doesn't matter
                final Collection<DesignerEdge> edges = agraph.getIncidentEdges(v);
                if (edges.isEmpty()) {
                    continue;
                }
                if (trace.get()) LOG.trace(catalog_tbl + " has " + edges.size() + " edges in AccessGraph");
                
                Histogram<Column> column_histogram = null;
                Histogram<Column> join_column_histogram = new Histogram<Column>();
                Histogram<Column> self_column_histogram = new Histogram<Column>();
                // Map<Column, Double> unsorted = new HashMap<Column, Double>();
                for (DesignerEdge e : edges) {
                    Collection<DesignerVertex> vertices = agraph.getIncidentVertices(e);
                    DesignerVertex v0 = CollectionUtil.get(vertices, 0);
                    DesignerVertex v1 = CollectionUtil.get(vertices, 1);
                    boolean self = (v0.equals(v) && v1.equals(v));
                    column_histogram = (self ? self_column_histogram : join_column_histogram);
                    
                    double edge_weight = e.getTotalWeight();
                    ColumnSet cset = e.getAttribute(AccessGraph.EdgeAttributes.COLUMNSET);
                    if (trace.get()) LOG.trace("Examining ColumnSet for " + e.toString(true));
                    
                    Histogram<Column> cset_histogram = cset.buildHistogramForType(Column.class);
                    if (trace.get()) LOG.trace("Constructed Histogram for " + catalog_tbl + " from ColumnSet:\n" + cset_histogram.toString());
                    
                    Collection<Column> columns = cset_histogram.values();
                    if (trace.get()) LOG.trace(cset_histogram.setDebugLabels(CatalogUtil.getHistogramLabels(cset_histogram.values())).toString(100, 50));
                    for (Column catalog_col : columns) {
                        if (!catalog_col.getParent().equals(catalog_tbl)) continue;
                        if (catalog_col.getNullable()) continue;
                        long cnt = cset_histogram.get(catalog_col);
                        if (trace.get()) LOG.trace("Found Match: " + catalog_col + " [cnt=" + cnt + "]");
                        column_histogram.put(catalog_col, Math.round(cnt * edge_weight));    
                    } // FOR
    //                System.err.println(cset.debug());
    //                LOG.info("[" + e.getTotalWeight() + "]: " + cset);
                } // FOR
                
                // If there were no join columns, then use the self-reference histogram
                column_histogram = (join_column_histogram.isEmpty() ? self_column_histogram : join_column_histogram); 
                if (column_histogram.isEmpty()) {
                    EventObserver observer = new EventObserver() {
                        @Override
                        public void update(Observable o, Object arg) {
                            DesignerVertex v = (DesignerVertex)arg;
                            
                            for (DesignerEdge e : agraph.getIncidentEdges(v)) {
                                System.err.println(e.getAttribute(AccessGraph.EdgeAttributes.COLUMNSET));
                            }
                            System.err.println(StringUtil.repeat("-", 100));
                        }
                    };
                    System.err.println("Edges: " + edges);
                    GraphVisualizationPanel.createFrame(agraph, observer).setVisible(true);
//                    ThreadUtil.sleep(10000);
                }

                // We might not find anything if we are calculating the lower bounds using only one transaction
//                if (column_histogram.isEmpty()) {
//                    if (trace.get()) LOG.trace("Failed to find any ColumnSets for " + catalog_tbl);
//                    continue;
//                }
                assert(!column_histogram.isEmpty()) : "Failed to find any ColumnSets for " + catalog_tbl;
                if (trace.get()) LOG.trace("Column Histogram:\n" + column_histogram);
                
                total_memory_ratio += (size_ratio / (double)info.getNumPartitions());
                total_memory_bytes += (ts.tuple_size_total / (double)info.getNumPartitions());
                Column most_popular = CollectionUtil.first(column_histogram.getMaxCountValues());
                pentry = new TableEntry(PartitionMethodType.HASH, most_popular, null, null);
                if (debug.get()) LOG.debug(String.format("PARTITION %-25s%s", catalog_tbl.getName(), most_popular.getName()));
            }
            pplan.table_entries.put(catalog_tbl, pentry);
        } // FOR
        assert(total_memory_ratio <= 100) : "Too much memory per partition: " + total_memory_ratio;
        for (Table catalog_tbl : info.catalog_db.getTables()) {
            if (pplan.getTableEntry(catalog_tbl) == null) {
                Column catalog_col = CollectionUtil.random(catalog_tbl.getColumns());
                assert(catalog_col != null) : "Failed to randomly pick column for " + catalog_tbl;
                pplan.table_entries.put(catalog_tbl, new TableEntry(PartitionMethodType.HASH, catalog_col, null, null));
                if (debug.get()) LOG.debug("RANDOM SELECTION: " + CatalogUtil.getDisplayName(catalog_col));
            }
        } // FOR
        
        if (hints.enable_procparameter_search) {
            if (debug.get()) LOG.debug("Selecting partitioning ProcParameter for " + this.info.catalog_db.getProcedures().size() + " Procedures");
            pplan.apply(info.catalog_db);
            
            // Temporarily disable multi-attribute parameters
            boolean multiproc_orig = hints.enable_multi_partitioning;
            hints.enable_multi_partitioning = false;
            
            for (Procedure catalog_proc : this.info.catalog_db.getProcedures()) {
                if (PartitionerUtil.shouldIgnoreProcedure(hints, catalog_proc)) continue;
                
                Set<String> param_order = PartitionerUtil.generateProcParameterOrder(info, info.catalog_db, catalog_proc, hints);
                if (param_order.isEmpty() == false) {
                    ProcParameter catalog_proc_param = CatalogKey.getFromKey(info.catalog_db, CollectionUtil.first(param_order), ProcParameter.class);
                    if (debug.get()) LOG.debug(String.format("PARTITION %-25s%s", catalog_proc.getName(), CatalogUtil.getDisplayName(catalog_proc_param)));
                    
                    // Create a new PartitionEntry for this procedure and set it to be always single-partitioned
                    // We will check down below whether that's always true or not
                    ProcedureEntry pentry = new ProcedureEntry(PartitionMethodType.HASH, catalog_proc_param, true);
                    pplan.getProcedureEntries().put(catalog_proc, pentry);
                }
            } // FOR
            
            hints.enable_multi_partitioning = multiproc_orig;
        }
        this.setProcedureSinglePartitionFlags(pplan, hints);
        
        this.last_memory = total_memory_bytes;
        
        return (pplan);
    }

}
