package com.taobao.tddl.executor.handler;

import com.taobao.tddl.common.exception.TddlException;
import com.taobao.tddl.executor.spi.CommandHandler;
import com.taobao.tddl.executor.spi.ExecutionContext;
import com.taobao.tddl.optimizer.OptimizerContext;
import com.taobao.tddl.optimizer.config.table.TableMeta;
import com.taobao.tddl.optimizer.core.plan.IPut;
import com.taobao.tddl.optimizer.core.plan.query.IQuery;

/**
 * @author mengshi.sunmengshi 2013-12-5 上午11:04:35
 * @since 5.1.0
 */
public abstract class HandlerCommon implements CommandHandler {

    protected TableMeta getTableMeta(String tableName) {
        TableMeta ts = OptimizerContext.getContext().getSchemaManager().getTable(tableName);
        return ts;
    }

    protected void buildTableAndMeta(IPut put, ExecutionContext executionContext) throws TddlException {
        String indexName = put.getIndexName();
        String groupDataNode = put.getDataNode();
        nestBuildTableAndSchema(groupDataNode, executionContext, indexName, put.getTableName(), true);
    }

    protected void buildTableAndMeta(IQuery query, ExecutionContext executionContext) throws TddlException {
        String indexName = query.getIndexName();
        String groupDataNode = query.getDataNode();

        nestBuildTableAndSchema(groupDataNode, executionContext, indexName, query.getTableName(), true);
    }

    /**
     * 取逻辑indexKey,而非实际index
     * 
     * @param query
     * @param executionContext
     * @throws Exception
     */
    protected void buildTableAndMetaLogicalIndex(IQuery query, ExecutionContext executionContext) throws TddlException {
        String indexName = query.getIndexName();
        String groupDataNode = query.getDataNode();
        nestBuildTableAndSchema(groupDataNode, executionContext, indexName, query.getTableName(), true);
    }

    protected void nestBuildTableAndSchema(String groupDataNode, ExecutionContext executionContext, String indexName,
                                           String actualTable, boolean logicalIndex) throws TddlException {
        if (indexName != null && !"".equals(indexName)) {
            String tableName = indexName.substring(0, indexName.indexOf('.'));
            TableMeta ts = null;

            ts = getTableMeta(tableName);

            if (ts == null) {
                throw new IllegalArgumentException("table :" + tableName + " is not found");
            }
            executionContext.setMeta(ts.getIndexMeta(indexName));
            executionContext.setTable(executionContext.getCurrentRepository().getTable(ts, groupDataNode, 0));
            executionContext.setActualTable(actualTable);
        }
    }

    public HandlerCommon(){
        super();
    }
}
