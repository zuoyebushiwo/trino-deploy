package com.zuoye.trino.delopy.anltr4.calculator.node;


import com.google.common.collect.Lists;

import java.util.List;

/**
 * @author ZuoYe
 * @Date 2023年04月02日
 */
public abstract class LeafNode extends AbstractNode {

    @Override
    public List<AlgebraNode> getInputs() {
        return Lists.newArrayList();
    }
}
