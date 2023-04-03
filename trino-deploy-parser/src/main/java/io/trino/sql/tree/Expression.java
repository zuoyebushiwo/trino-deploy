package io.trino.sql.tree;

import io.trino.sql.ExpressionFormatter;

import java.util.Optional;

/**
 * @author ZhangXueJun
 * @Date 2023年04月03日
 */
public abstract class Expression extends Node {

    public Expression(Optional<NodeLocation> location) {
        super(location);
    }

    @Override
    public <R, C> R accept(AstVisitor<R, C> visitor, C context) {
        return visitor.visitExpression(this, context);
    }

    @Override
    public String toString() {
        return ExpressionFormatter.formatExpression(this);
    }
}
