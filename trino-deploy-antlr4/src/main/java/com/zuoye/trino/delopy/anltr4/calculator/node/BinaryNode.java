package com.zuoye.trino.delopy.anltr4.calculator.node;

import com.google.common.collect.Lists;

import java.util.List;

/**
 * @author ZuoYe
 * @Date 2023年04月02日
 */
public abstract class BinaryNode extends AbstractNode implements AlgebraNode {

    private AlgebraNode left;
    private AlgebraNode right;

    public BinaryNode(AlgebraNode left, AlgebraNode right) {
        this.left = left;
        this.right = right;
    }

    public AlgebraNode getLeft() {
        return left;
    }

    public AlgebraNode getRight() {
        return right;
    }

    @Override
    public List<AlgebraNode> getInputs() {
        return Lists.newArrayList(left, right);
    }
}
