package io.trino.sql.tree;

import javax.annotation.Nullable;

/**
 * SQL语法树：访问者
 *
 * @author ZhangXueJun
 * @Date 2023年04月03日
 */
public abstract class AstVisitor<R, C> {

    /**
     * 访问SQL节点
     *
     * @param node
     * @return
     */
    public R process(Node node) {
        return process(node, null);
    }

    public R process(Node node, @Nullable C context) {
        return node.accept(this, context);
    }

    protected R visitNode(Node node, C context) {
        return null;
    }

    protected R visitExpression(Expression node, C context) {
        return visitNode(node, context);
    }

    public R visitSymbolReference(SymbolReference node, C context) {
        return visitExpression(node, context);
    }

    public R visitIdentifier(Identifier node, C context) {
        return visitExpression(node, context);
    }

    public R visitPathElement(PathElement node, C context) {
        return visitNode(node, context);
    }

    public R visitPathSpecification(PathSpecification node, C context) {
        return visitNode(node, context);
    }
}
