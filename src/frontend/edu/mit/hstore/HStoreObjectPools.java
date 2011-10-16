package edu.mit.hstore;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.collections15.map.ListOrderedMap;
import org.apache.commons.pool.impl.StackObjectPool;

import edu.brown.utils.TypedStackObjectPool;
import edu.mit.hstore.callbacks.TransactionFinishCallback;
import edu.mit.hstore.callbacks.TransactionInitCallback;
import edu.mit.hstore.callbacks.TransactionInitWrapperCallback;
import edu.mit.hstore.callbacks.TransactionPrepareCallback;
import edu.mit.hstore.callbacks.TransactionRedirectCallback;
import edu.mit.hstore.callbacks.TransactionRedirectResponseCallback;
import edu.mit.hstore.callbacks.TransactionWorkCallback;
import edu.mit.hstore.dtxn.DependencyInfo;
import edu.mit.hstore.dtxn.LocalTransaction;
import edu.mit.hstore.dtxn.RemoteTransaction;

public abstract class HStoreObjectPools {

    // ----------------------------------------------------------------------------
    // CALLBACKS
    // ----------------------------------------------------------------------------
    
    /**
     * 
     */
    public static TypedStackObjectPool<TransactionInitCallback> CALLBACKS_TXN_INIT;
    
    /**
     * 
     */
    public static TypedStackObjectPool<TransactionInitWrapperCallback> CALLBACKS_TXN_INITWRAPPER;
    
    /**
     * 
     */
    public static TypedStackObjectPool<TransactionWorkCallback> CALLBACKS_TXN_WORK;

    /**
     * 
     */
    public static TypedStackObjectPool<TransactionPrepareCallback> CALLBACKS_TXN_PREPARE;
    
    /**
     * 
     */
    public static TypedStackObjectPool<TransactionFinishCallback> CALLBACKS_TXN_FINISH;
    
    /**
     * ForwardTxnRequestCallback Pool
     */
    public static TypedStackObjectPool<TransactionRedirectCallback> CALLBACKS_TXN_REDIRECT_REQUEST;
    
    /**
     * ForwardTxnResponseCallback Pool
     */
    public static TypedStackObjectPool<TransactionRedirectResponseCallback> CALLBACKS_TXN_REDIRECTRESPONSE;
    
    // ----------------------------------------------------------------------------
    // INTERNAL STATE OBJECTS
    // ----------------------------------------------------------------------------
    
    /**
     * LocalTransaction State ObjectPool
     */
    public static TypedStackObjectPool<LocalTransaction> STATES_TXN_LOCAL;
    
    /**
     * RemoteTransaction State ObjectPool
     */
    public static TypedStackObjectPool<RemoteTransaction> STATES_TXN_REMOTE;
    
    /**
     * DependencyInfo ObjectPool
     */
    public static TypedStackObjectPool<DependencyInfo> STATES_DEPENDENCYINFO;

    
    // ----------------------------------------------------------------------------
    // INITIALIZATION
    // ----------------------------------------------------------------------------
    
    /**
     * 
     * @param hstore_site
     */
    public synchronized static void initialize(HStoreSite hstore_site) {
        assert(hstore_site != null);
        if (CALLBACKS_TXN_REDIRECT_REQUEST == null) {
            HStoreConf hstore_conf = hstore_site.getHStoreConf();
            
            CALLBACKS_TXN_INIT = TypedStackObjectPool.factory(TransactionInitCallback.class,
                    hstore_conf.site.pool_localtxninit_idle,
                    hstore_conf.site.pool_profiling, hstore_site);
            CALLBACKS_TXN_INITWRAPPER = TypedStackObjectPool.factory(TransactionInitWrapperCallback.class,
                    hstore_conf.site.pool_localtxninit_idle,
                    hstore_conf.site.pool_profiling, hstore_site);
            CALLBACKS_TXN_WORK = TypedStackObjectPool.factory(TransactionWorkCallback.class,
                    hstore_conf.site.pool_forwardtxnresponses_idle,
                    hstore_conf.site.pool_profiling, hstore_site);
            CALLBACKS_TXN_PREPARE = TypedStackObjectPool.factory(TransactionPrepareCallback.class,
                    hstore_conf.site.pool_txnprepare_idle,
                    hstore_conf.site.pool_profiling, hstore_site);
            CALLBACKS_TXN_FINISH = TypedStackObjectPool.factory(TransactionFinishCallback.class,
                    hstore_conf.site.pool_txnprepare_idle,
                    hstore_conf.site.pool_profiling, hstore_site);
            
            CALLBACKS_TXN_REDIRECT_REQUEST = TypedStackObjectPool.factory(TransactionRedirectCallback.class,
                    hstore_conf.site.pool_forwardtxnrequests_idle,
                    hstore_conf.site.pool_profiling);
            CALLBACKS_TXN_REDIRECTRESPONSE = TypedStackObjectPool.factory(TransactionRedirectResponseCallback.class,
                    hstore_conf.site.pool_forwardtxnresponses_idle,
                    hstore_conf.site.pool_profiling);

            STATES_TXN_LOCAL = TypedStackObjectPool.factory(LocalTransaction.class,
                    hstore_conf.site.pool_localtxnstate_idle,
                    hstore_conf.site.pool_profiling, hstore_site);
            STATES_TXN_REMOTE = TypedStackObjectPool.factory(RemoteTransaction.class,
                    hstore_conf.site.pool_remotetxnstate_idle,
                    hstore_conf.site.pool_profiling, hstore_site);
            STATES_DEPENDENCYINFO = TypedStackObjectPool.factory(DependencyInfo.class,
                    hstore_conf.site.pool_dependencyinfos_idle,
                    hstore_conf.site.pool_profiling);
        }
        for (Entry<String, StackObjectPool> e : getAllPools().entrySet()) {
            assert(e.getValue() != null) : e.getKey() + " is null!";
        } // FOR
    }
    
    public static Map<String, StackObjectPool> getAllPools() {
        Map<String, StackObjectPool> m = new ListOrderedMap<String, StackObjectPool>();
        
        Object val = null;
        for (Field f : HStoreObjectPools.class.getFields()) {
            try {
                val = f.get(null);
                if (val instanceof TypedStackObjectPool<?>) {
                    m.put(f.getName(), (StackObjectPool)val);
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        
        return (m);
    }
}