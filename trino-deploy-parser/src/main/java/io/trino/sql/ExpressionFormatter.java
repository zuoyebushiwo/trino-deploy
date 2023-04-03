package io.trino.sql;

import io.trino.sql.tree.AstVisitor;
import io.trino.sql.tree.Expression;
import io.trino.sql.tree.Node;

import static java.lang.String.format;

/**
 * @author ZhangXueJun
 * @Date 2023年04月03日
 */
public final class ExpressionFormatter {

    public static String formatExpression(Expression expression) {
        return new Formatter().process(expression, null);
    }

    public static class Formatter extends AstVisitor<String, Void> {

        @Override
        protected String visitNode(Node node, Void context) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected String visitExpression(Expression node, Void context) {
            throw new UnsupportedOperationException(format("not yet implemented: %s.visit%s", getClass().getName(), node.getClass().getSimpleName()));
        }
    }

}
