package com.zuoye.trino.delopy.anltr4.calculator.node;

import com.zuoye.trino.delopy.anltr4.calculator.type.DataType;

import java.util.List;

/**
 * @author ZuoYe
 * @Date 2023年04月02日
 */
public interface AlgebraNode {
    DataType getType();

    List<AlgebraNode> getInputs();

    String generateCode();
}
