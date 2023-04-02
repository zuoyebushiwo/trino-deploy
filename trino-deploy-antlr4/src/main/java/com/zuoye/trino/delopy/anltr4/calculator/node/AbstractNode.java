package com.zuoye.trino.delopy.anltr4.calculator.node;

import com.zuoye.trino.delopy.anltr4.calculator.type.DataType;

import java.util.List;

/**
 * @author ZuoYe
 * @Date 2023年04月02日
 */
public abstract class AbstractNode implements AlgebraNode {

    private transient DataType dataType;

    @Override
    public DataType getType() {
        if (dataType == null) {
            dataType = inferTypeFromInputs();
        }
        return dataType;
    }

    private DataType inferTypeFromInputs() {
        List<AlgebraNode> inputs = getInputs();
        assert !inputs.isEmpty();
        for (AlgebraNode input : inputs) {
            if (input.getType() == DataType.DOUBLE) {
                return DataType.DOUBLE;
            }
        }
        return DataType.LONG;
    }
}
