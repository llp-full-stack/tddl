package com.taobao.tddl.executor.handler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.taobao.tddl.common.exception.TddlException;
import com.taobao.tddl.executor.common.KVPair;
import com.taobao.tddl.executor.cursor.IAffectRowCursor;
import com.taobao.tddl.executor.cursor.ISchematicCursor;
import com.taobao.tddl.executor.record.CloneableRecord;
import com.taobao.tddl.executor.rowset.IRowSet;
import com.taobao.tddl.executor.spi.ExecutionContext;
import com.taobao.tddl.executor.spi.ITHLog;
import com.taobao.tddl.executor.spi.Repository;
import com.taobao.tddl.executor.spi.Table;
import com.taobao.tddl.executor.spi.Transaction;
import com.taobao.tddl.executor.spi.TransactionConfig;
import com.taobao.tddl.monitor.Monitor;
import com.taobao.tddl.optimizer.config.table.IndexMeta;
import com.taobao.tddl.optimizer.core.plan.IDataNodeExecutor;
import com.taobao.tddl.optimizer.core.plan.IPut;

public abstract class PutHandlerCommon extends HandlerCommon {

    public PutHandlerCommon(){
        super();
    }

    public Log logger = LogFactory.getLog(PutHandlerCommon.class);

    @SuppressWarnings("rawtypes")
    public ISchematicCursor handle(IDataNodeExecutor executor, ExecutionContext executionContext) throws TddlException {
        long time = System.currentTimeMillis();
        IPut put = (IPut) executor;

        // 用于测试终止任务 Thread.sleep(1000000000l);
        buildTableAndMeta(put, executionContext);

        int affect_rows = 0;
        Transaction transaction = executionContext.getTransaction();
        Table table = executionContext.getTable();
        IndexMeta meta = executionContext.getMeta();
        boolean autoCommit = false;
        try {
            if (transaction == null) {// 客户端没有用事务，这里手动加上。
                Repository repo = executionContext.getCurrentRepository();
                if (repo.getRepoConfig().isTransactional()) {
                    transaction = repo.beginTransaction(getDefalutTransactionConfig(repo));
                    executionContext.setTransaction(transaction);
                    autoCommit = true;
                }
            }
            affect_rows = executePut(executionContext, put, table, meta);
            if (autoCommit) {
                commit(executionContext, transaction);
            }
        } catch (Exception e) {
            time = Monitor.monitorAndRenewTime(Monitor.KEY1, Monitor.ServerPut, Monitor.Key3Fail, time);
            if (autoCommit) {

                rollback(executionContext, transaction);
            }
            throw new TddlException(e);
        }

        // 这里返回key->value的方式的东西，类似Key=affectRow val=1 这样的软编码
        IAffectRowCursor affectrowCursor = executionContext.getCurrentRepository()
            .getCursorFactory()
            .affectRowCursor(executionContext, affect_rows);

        time = Monitor.monitorAndRenewTime(Monitor.KEY1, Monitor.ServerPut, Monitor.Key3Success, time);
        return affectrowCursor;

    }

    // move to JE_Transaction
    protected void rollback(ExecutionContext executionContext, Transaction transaction) {
        try {
            // if (historyLog.get() != null) {
            // historyLog.get().rollback(transaction);
            // }
            transaction.rollback();
            executionContext.setTransaction(null);
        } catch (Exception ex) {
            logger.error("", ex);
        }
    }

    protected void commit(ExecutionContext executionContext, Transaction transaction) throws TddlException {
        transaction.commit();
        // 清空当前事务运行状态
        executionContext.setTransaction(null);
    }

    protected abstract int executePut(ExecutionContext executionContext, IPut put, Table table, IndexMeta meta)
                                                                                                               throws Exception;

    protected void prepare(Transaction transaction, Table table, IRowSet oldkv, CloneableRecord key,
                           CloneableRecord value, IPut.PUT_TYPE putType) throws TddlException {
        ITHLog historyLog = transaction.getHistoryLog();
        if (historyLog != null) {
            historyLog.parepare(transaction.getId(), table.getSchema(), putType, oldkv, new KVPair(key, value));
        }
    }

    protected TransactionConfig getDefalutTransactionConfig(Repository repo) {
        TransactionConfig tc = new TransactionConfig();
        String isolation = repo.getRepoConfig().getDefaultTnxIsolation();
        // READ_UNCOMMITTED|READ_COMMITTED|REPEATABLE_READ|SERIALIZABLE
        if ("READ_UNCOMMITTED".equals(isolation)) {
            tc.setReadUncommitted(true);
        } else if ("READ_COMMITTED".equals(isolation)) {
            tc.setReadCommitted(true);
        }
        return tc;
    }

}
