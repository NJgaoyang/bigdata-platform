package com.company.platform.query;

import org.springframework.stereotype.Component;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLDeleteStatement;
import com.alibaba.druid.sql.ast.statement.SQLDropDatabaseStatement;
import com.alibaba.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.druid.sql.ast.statement.SQLTruncateStatement;
import com.alibaba.druid.sql.ast.statement.SQLUpdateStatement;

@Component
public class SqlSafetyChecker {
    public CheckResult check(String sql) {
        if (sql == null || sql.isBlank()) return new CheckResult(false, "SQL 不能为空");
        try {
            SQLStatement statement = SQLUtils.parseSingleStatement(sql, DbType.mysql);
            if (statement instanceof SQLDropDatabaseStatement) return deny("禁止执行 DROP DATABASE");
            if (statement instanceof SQLDropTableStatement) return deny("禁止执行 DROP TABLE");
            if (statement instanceof SQLTruncateStatement) return deny("禁止执行 TRUNCATE");
            if (statement instanceof SQLDeleteStatement delete && delete.getWhere() == null) return deny("DELETE 必须包含 WHERE");
            if (statement instanceof SQLUpdateStatement update && update.getWhere() == null) return deny("UPDATE 必须包含 WHERE");
            return new CheckResult(true, "SQL 安全检查通过");
        } catch (RuntimeException ex) {
            return deny("SQL 解析失败，已阻止执行：" + ex.getMessage());
        }
    }
    private CheckResult deny(String message) { return new CheckResult(false, message); }
    public record CheckResult(boolean safe, String message) { }
}
