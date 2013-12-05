package com.taobao.tddl.repo.mysql.spi;

import java.sql.DatabaseMetaData;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.taobao.tddl.common.exception.TddlException;
import com.taobao.tddl.common.utils.GeneralUtil;
import com.taobao.tddl.executor.common.DuplicateKVPair;
import com.taobao.tddl.executor.common.ICursorMeta;
import com.taobao.tddl.executor.common.KVPair;
import com.taobao.tddl.executor.cursor.Cursor;
import com.taobao.tddl.executor.cursor.ISchematicCursor;
import com.taobao.tddl.executor.record.CloneableRecord;
import com.taobao.tddl.executor.record.FixedLengthRecord;
import com.taobao.tddl.executor.record.NamedRecord;
import com.taobao.tddl.executor.rowset.IRowSet;
import com.taobao.tddl.optimizer.config.table.ColumnMeta;
import com.taobao.tddl.optimizer.core.expression.IColumn;
import com.taobao.tddl.optimizer.core.expression.IFilter.OPERATION;
import com.taobao.tddl.optimizer.core.expression.ISelectable.DATA_TYPE;
import com.taobao.tddl.optimizer.core.plan.IDataNodeExecutor;
import com.taobao.tddl.optimizer.core.plan.query.IQuery;

public class My_Cursor implements Cursor, My_Condensable {

    protected My_JdbcHandler    myJdbcHandler;
    protected IDataNodeExecutor query;
    protected String            groupNodeName;
    protected ICursorMeta       meta;
    protected boolean           inited        = false;
    // private boolean directlyExecuteSql = false;

    protected boolean           isStreaming   = false;
    protected List<ColumnMeta>  returnColumns = null;

    public My_Cursor(My_JdbcHandler myJdbcHandler, ICursorMeta meta, String groupNodeName, IDataNodeExecutor executor,
                     boolean isStreaming){
        super();
        this.myJdbcHandler = myJdbcHandler;
        this.query = executor;
        this.groupNodeName = groupNodeName;
        this.meta = meta;
        this.isStreaming = isStreaming;
        // init();
    }

    // public My_Cursor() {
    // }

    @Override
    public boolean skipTo(CloneableRecord key) throws TddlException {
        init();
        return true;
    }

    @Override
    public boolean skipTo(KVPair key) throws TddlException {
        throw new UnsupportedOperationException("not support yet");
    }

    @Override
    public IRowSet current() throws TddlException {
        init();
        return myJdbcHandler.getCurrent();
    }

    @Override
    public IRowSet next() throws TddlException {
        init();
        return myJdbcHandler.next();
    }

    public void init() throws TddlException {
        if (inited) {
            return;
        }

        String groupName = getGroupNodeName();
        if (groupName == null) {
            // directlyExecuteSql = false;
            throw new IllegalArgumentException("should not be here");
        } else {
            // directlyExecuteSql = true;
            myJdbcHandler.executeQuery(meta, isStreaming);

        }

        ResultSetMetaData rsmd = this.myJdbcHandler.getResultSet().getMetaData();
        DatabaseMetaData dbmd = myJdbcHandler.getConnection().getMetaData();
        returnColumns = new ArrayList();
        for (int i = 1; i <= rsmd.getColumnCount(); i++) {
            DATA_TYPE type = TableSchemaParser.jdbcTypeToAndorType(rsmd.getColumnType(i));
            if (type == null) throw new IllegalArgumentException("列：" + rsmd.getColumnName(i) + " 类型"
                                                                 + rsmd.getColumnType(i) + "无法识别,联系沈询");

            String name = rsmd.getColumnLabel(i);
            // String tableName = rsmd.getTableName(i);
            ColumnMeta cm = new ColumnMeta(null, name, type);
            returnColumns.add(cm);
        }
        inited = true;
    }

    public ISchematicCursor getResultSet() throws TddlException {
        init();

        return myJdbcHandler.getResultCursor();
    }

    @Override
    public IRowSet prev() throws TddlException {
        isStreaming = false;
        init();
        return myJdbcHandler.prev();
    }

    @Override
    public IRowSet first() throws TddlException {
        init();
        return myJdbcHandler.first();
    }

    @Override
    public IRowSet last() throws TddlException {
        init();
        return myJdbcHandler.last();
    }

    @Override
    public boolean delete() throws TddlException {
        throw new UnsupportedOperationException("not support yet");
    }

    @Override
    public IRowSet getNextDup() throws TddlException {
        throw new UnsupportedOperationException("not support yet");
    }

    @Override
    public void put(CloneableRecord key, CloneableRecord value) throws TddlException {
        throw new UnsupportedOperationException("not support yet");
    }

    // =======================================================================
    // ==============Getters and Setters=======

    // public OneQuery getSql() {
    // oneQ = OneQuery.buildOne(new OneQuery(), iQuery);
    // oneQ.isJoin = false;
    // return oneQ;
    // }

    public ICursorMeta getCursorMeta() {
        return meta;
    }

    public void setCursorMeta(ICursorMeta cursorMeta) {
        this.meta = cursorMeta;
    }

    public String getGroupNodeName() {
        return groupNodeName;
    }

    public void setGroupNodeName(String groupNodeName) {
        this.groupNodeName = groupNodeName;
    }

    // @Override
    // public boolean canCondense() {
    // return true;
    // }

    public IDataNodeExecutor getiQuery() {
        return query;
    }

    public void setiQuery(IQuery iQuery) {
        this.query = iQuery;
    }

    @Override
    public List<Exception> close(List<Exception> exs) {
        if (exs == null) exs = new ArrayList();
        try {
            myJdbcHandler.close();
        } catch (Exception e) {
            exs.add(e);
        }

        return exs;
    }

    public int sizeLimination = 10000;

    @Override
    public Map<CloneableRecord, DuplicateKVPair> mgetWithDuplicate(List<CloneableRecord> keys, boolean prefixMatch,
                                                                   boolean keyFilterOrValueFilter) throws TddlException {
        // oneQ = OneQuery.buildOne(new OneQuery(), iQuery);

        IQuery tmpQuery = (IQuery) query.copySelf();

        List<Comparable> values = new ArrayList<Comparable>();
        String cm = keys.get(0).getColumnList().get(0);
        for (CloneableRecord key : keys) {
            values.add((Comparable) key.get(cm));
        }
        IColumn ic = new PBColumnAdapter();
        ic.setColumnName(cm);

        PBBooleanFilterAdapter targetFilter = new PBBooleanFilterAdapter();
        targetFilter.setOperation(OPERATION.IN);
        targetFilter.setColumn(ic);
        targetFilter.setValues(values);
        // OneQuery.appendAndFilter(oneQ, targetFilter);

        BoolUtil e = new BoolUtil(this.ac);
        tmpQuery.setKeyFilter(e.and(tmpQuery.getKeyFilter(), targetFilter));

        myJdbcHandler.setPlan(tmpQuery);
        myJdbcHandler.executeQuery(this.meta, isStreaming);
        Map<CloneableRecord, DuplicateKVPair> res = buildDuplicateKVPair(keys);
        return res;
    }

    // ==============Getters and Setters=======

    public Map<CloneableRecord, DuplicateKVPair> buildDuplicateKVPair(List<CloneableRecord> keys) throws TddlException {
        String cmStr = keys.get(0).getColumnList().get(0);
        ColumnMeta cm = new ColumnMeta(getCursorMeta().getColumns().get(0).getTableName(), cmStr, null);
        List<ColumnMeta> cms = new LinkedList<ColumnMeta>();
        cms.add(cm);
        IRowSet rowSet = null;
        int count = 0;
        Map<CloneableRecord, DuplicateKVPair> duplicateKeyMap = new HashMap<CloneableRecord, DuplicateKVPair>();
        while ((rowSet = myJdbcHandler.next()) != null) {
            CloneableRecord value = new FixedLengthRecord(cms);
            CloneableRecord key = new NamedRecord(cmStr, value);
            rowSet = GeneralUtil.fromIRowSetToArrayRowSet(rowSet);
            Object v = GeneralUtil.getValueByColumnMeta(rowSet, cm);
            value.put(cmStr, v);
            DuplicateKVPair tempKVPair = duplicateKeyMap.get(key);
            if (tempKVPair == null) {// 加新列
                tempKVPair = new DuplicateKVPair(rowSet);
                duplicateKeyMap.put(key, tempKVPair);
            } else {// 加重复列

                while (tempKVPair.next != null) {
                    tempKVPair = tempKVPair.next;
                }

                tempKVPair.next = new DuplicateKVPair(rowSet);
            }
            count++;
            if (count >= sizeLimination) {// 保护。。。别太多了
                throw new IllegalArgumentException("size is more than limination " + sizeLimination);
            }
        }
        if (rowSet == null) {
            myJdbcHandler.closeResultSetAndConnection();
        }
        return duplicateKeyMap;
    }

    @Override
    public List<DuplicateKVPair> mgetWithDuplicateList(List<CloneableRecord> keys, boolean prefixMatch,
                                                       boolean keyFilterOrValueFilter) throws TddlException {
        Map<CloneableRecord, DuplicateKVPair> map = mgetWithDuplicate(keys, prefixMatch, keyFilterOrValueFilter);
        return new ArrayList<DuplicateKVPair>(map.values());
    }

    @Override
    public String toStringWithInden(int inden) {

        String tabTittle = GeneralUtil.getTab(inden);
        String tabContent = GeneralUtil.getTab(inden + 1);
        StringBuilder sb = new StringBuilder();

        GeneralUtil.printlnToStringBuilder(sb, tabTittle + "MyCursor ");
        if (meta != null) {
            GeneralUtil.printAFieldToStringBuilder(sb, "meta", this.meta, tabContent);
        }

        GeneralUtil.printAFieldToStringBuilder(sb, "isStreaming", this.isStreaming, tabContent);

        if (this.myJdbcHandler != null) GeneralUtil.printAFieldToStringBuilder(sb,
            "plan",
            this.myJdbcHandler.getPlan(),
            tabContent);

        return sb.toString();
    }

    @Override
    public void beforeFirst() throws TddlException {
        init();
        myJdbcHandler.beforeFirst();
    }

    public List<ColumnMeta> getReturnColumns() throws TddlException {
        init();
        return this.returnColumns;
    }

    @Override
    public boolean isDone() {
        return true;
    }

}
