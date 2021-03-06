package edu.brown.api;

import org.voltdb.SysProcSelector;

public abstract class BenchmarkControllerUtil {

    public static class ProfilingOutput {
        public final SysProcSelector key;
        public final String clientParam;
        public final String siteParam;
        private ProfilingOutput(SysProcSelector key, String clientParam, String siteParam) {
            this.key = key;
            this.clientParam = clientParam;
            this.siteParam = siteParam;
        }
        @Override
        public String toString() {
            return String.format("%s/%s/%s", this.key, this.clientParam, this.siteParam);
        }
    }
    
    /**
     * For each client output option, we'll enable the corresponding
     * site config parameter so that we can collect the proper data
     */
    public static final ProfilingOutput[] PROFILING_OUTPUTS = {
        new ProfilingOutput(SysProcSelector.TABLE, "client.output_table_stats", null),
        new ProfilingOutput(SysProcSelector.EXECPROFILER, "client.output_exec_profiling", "site.exec_profiling"),
        new ProfilingOutput(SysProcSelector.QUEUEPROFILER, "client.output_queue_profiling", "site.queue_profiling"),
        new ProfilingOutput(SysProcSelector.TXNPROFILER, "client.output_txn_profiling", "site.txn_profiling"),
        new ProfilingOutput(SysProcSelector.SITEPROFILER, "client.output_site_profiling", "site.profiling"),
        new ProfilingOutput(SysProcSelector.SPECEXECPROFILER, "client.output_specexec_profiling", "site.specexec_profiling"),
        new ProfilingOutput(SysProcSelector.MARKOVPROFILER, "client.output_markov_profiling", "site.markov_profiling"),
        new ProfilingOutput(SysProcSelector.PLANNERPROFILER, "client.output_planner_profiling", "site.planner_profiling"),
        new ProfilingOutput(SysProcSelector.TXNCOUNTER, "client.output_txn_counters", "site.txn_counters"),
        new ProfilingOutput(SysProcSelector.ANTICACHE, "client.output_anticache_profiling", "site.anticache_profiling"),
        new ProfilingOutput(SysProcSelector.ANTICACHEEVICTIONS, "client.output_anticache_evictions", "site.anticache_profiling"),
        new ProfilingOutput(SysProcSelector.ANTICACHEACCESS, "client.output_anticache_access", "site.anticache_access"),
    };
    
    
    public static String getClientName(String host, int id) {
        return String.format("%s-%03d", host, id);
    }
    
}
