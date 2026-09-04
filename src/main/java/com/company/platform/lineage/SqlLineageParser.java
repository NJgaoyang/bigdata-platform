package com.company.platform.lineage;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.druid.sql.ast.statement.SQLInsertStatement;
import com.alibaba.druid.sql.visitor.SQLASTVisitorAdapter;
import com.alibaba.druid.util.JdbcConstants;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class SqlLineageParser {
    public ParseResult parse(String sql) {
        Set<String> sources = new LinkedHashSet<>();
        Set<String> targets = new LinkedHashSet<>();
        String text = sql == null ? "" : sql;
        List<SQLStatement> statements;
        try {
            statements = SQLUtils.parseStatements(text, JdbcConstants.MYSQL);
        } catch (RuntimeException ex) {
            return new ParseResult(sources, targets);
        }
        for (SQLStatement statement : statements) {
            if (statement instanceof SQLInsertStatement insert && insert.getTableName() != null) {
                targets.add(insert.getTableName().toString());
            }
            if (statement instanceof SQLCreateTableStatement create && create.getName() != null) {
                targets.add(create.getName().toString());
            }
            statement.accept(new SQLASTVisitorAdapter() {
                @Override public boolean visit(SQLExprTableSource tableSource) {
                    if (tableSource.getExpr() != null) sources.add(tableSource.getExpr().toString());
                    return true;
                }
            });
        }
        sources.removeAll(targets);
        return new ParseResult(sources, targets);
    }
    public record ParseResult(Set<String> sources, Set<String> targets) { }
}
