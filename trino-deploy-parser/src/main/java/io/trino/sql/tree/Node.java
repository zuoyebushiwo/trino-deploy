package io.trino.sql.tree;

import java.util.List;
import java.util.Optional;

/**
 * SQL语法树：解析节点
 *
 * @author ZhangXueJun
 * @Date 2023年04月03日
 */
public abstract class Node {

    private final Optional<NodeLocation> location;

    public Node(Optional<NodeLocation> location) {
        this.location = location;
    }

    /**
     * Accessible for {@link AstVisitor}, use {@link AstVisitor#process(Node, Object)} instead.
     */
    public abstract <R, C> R accept(AstVisitor<R, C> visitor, C context);

    public Optional<NodeLocation> getLocation() {
        return location;
    }

    /**
     * 下游SQL解析节点
     *
     * @return
     */
    public abstract List<? extends Node> getChildren();

    // Force subclasses to have a proper equals and hashcode implementation
    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract String toString();

    /**
     * Compare with another node by considering internal state excluding any Node returned by getChildren()
     */
    public boolean shallowEquals(Node other) {
        throw new UnsupportedOperationException("not yet implemented: " + getClass().getName());
    }

    static boolean sameClass(Node left, Node right) {
        if (left == right) {
            return true;
        }

        return left.getClass() == right.getClass();
    }
}
