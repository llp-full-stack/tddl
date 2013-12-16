package com.taobao.tddl.optimizer.rule;

import com.taobao.tddl.common.model.lifecycle.AbstractLifecycle;
import com.taobao.tddl.optimizer.config.table.IndexManager;
import com.taobao.tddl.optimizer.config.table.IndexMeta;

/**
 * 基于{@linkplain RuleSchemaManager}完成index的获取
 * 
 * @since 5.1.0
 */
public class RuleIndexManager extends AbstractLifecycle implements IndexManager {

    private RuleSchemaManager schemaManager;

    public RuleIndexManager(RuleSchemaManager schemaManager){
        this.schemaManager = schemaManager;
    }

    public IndexMeta getIndexByName(String name) {
        int index = name.indexOf(".");
        if (index < 0) {
            index = name.length();
        }
        String tableName = name.substring(0, index);
        return schemaManager.getTable(tableName).getIndexMeta(name);
    }
}
