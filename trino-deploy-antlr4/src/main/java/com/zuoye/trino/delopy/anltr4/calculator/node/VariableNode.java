package com.zuoye.trino.delopy.anltr4.calculator.node;

import com.zuoye.trino.delopy.anltr4.calculator.type.DataType;

/**
 * @author ZuoYe
 * @Date 2023年04月02日
 */
public class VariableNode extends LeafNode {
    private final DataType type;
    private final String name;

    public VariableNode(DataType type, String name) {
        this.type = type;
        this.name = name;
    }

    @Override
    public DataType getType() {
        return type;
    }

    @Override
    public String generateCode() {
        return name;
    }
}
