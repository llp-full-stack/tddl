package com.taobao.tddl.executor.handler;

import java.util.List;

import com.taobao.tddl.common.exception.TddlException;
import com.taobao.tddl.common.utils.ExceptionErrorCodeUtils;
import com.taobao.tddl.executor.codec.CodecFactory;
import com.taobao.tddl.executor.function.ExtraFunction;
import com.taobao.tddl.executor.record.CloneableRecord;
import com.taobao.tddl.executor.rowset.IRowSet;
import com.taobao.tddl.executor.spi.ExecutionContext;
import com.taobao.tddl.executor.spi.Table;
import com.taobao.tddl.executor.spi.Transaction;
import com.taobao.tddl.executor.utils.ExecUtils;
import com.taobao.tddl.optimizer.config.table.ColumnMeta;
import com.taobao.tddl.optimizer.config.table.IndexMeta;
import com.taobao.tddl.optimizer.core.expression.IFunction;
import com.taobao.tddl.optimizer.core.plan.IPut;
import com.taobao.tddl.optimizer.core.plan.IPut.PUT_TYPE;

public class InsertHandler extends PutHandlerCommon {

    public InsertHandler(){
        super();
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected int executePut(ExecutionContext executionContext, IPut put, Table table, IndexMeta meta)
                                                                                                      throws TddlException {
        Transaction transaction = executionContext.getTransaction();
        int affect_rows = 0;
        IPut insert = (IPut) put;
        CloneableRecord key = CodecFactory.getInstance(CodecFactory.FIXED_LENGTH)
            .getCodec(meta.getKeyColumns())
            .newEmptyRecord();
        CloneableRecord value = CodecFactory.getInstance(CodecFactory.FIXED_LENGTH)
            .getCodec(meta.getValueColumns())
            .newEmptyRecord();
        List columns = insert.getUpdateColumns();
        L: for (int i = 0; i < columns.size(); i++) {
            for (ColumnMeta cm : meta.getKeyColumns()) {
                if (cm.getName().equals(ExecUtils.getColumn(columns.get(i)).getColumnName())) {
                    Object v = insert.getUpdateValues().get(i);
                    // if (v instanceof NullVal) {
                    // v = null;
                    // }
                    if (v instanceof IFunction) {
                        IFunction func = ((IFunction) v);
                        ((ExtraFunction) func.getExtraFunction()).serverMap((IRowSet) null);
                        v = func.getExtraFunction().getResult();
                    }
                    key.put(cm.getName(), v);
                    continue L;
                }
            }
            for (ColumnMeta cm : meta.getValueColumns()) {
                if (cm.getName().equals(ExecUtils.getColumn(columns.get(i)).getColumnName())) {
                    Object v = insert.getUpdateValues().get(i);
                    if (v instanceof IFunction) {
                        IFunction func = ((IFunction) v);

                        ((ExtraFunction) func.getExtraFunction()).serverMap((IRowSet) null);
                        v = func.getExtraFunction().getResult();
                    }
                    value.put(cm.getName(), v);
                    break;
                }
            }
        }
        if (put.getPutType() == IPut.PUT_TYPE.INSERT) {
            CloneableRecord value1 = table.get(transaction, key, meta, executionContext.getDbName());
            if (value1 != null) {
                throw new TddlException(ExceptionErrorCodeUtils.Duplicate_entry, "exception insert existed :" + key);
            }
        }
        prepare(transaction, table, null, key, value, PUT_TYPE.INSERT);
        table.put(transaction, key, value, meta, executionContext.getDbName());
        affect_rows++;
        return affect_rows;

    }

}
