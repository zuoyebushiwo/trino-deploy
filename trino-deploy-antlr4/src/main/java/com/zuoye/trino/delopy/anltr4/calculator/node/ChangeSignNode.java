package com.zuoye.trino.delopy.anltr4.calculator.node;

public class ChangeSignNode extends UnaryNode implements AlgebraNode {

    public ChangeSignNode(AlgebraNode input) {
        super(input);
    }

    @Override
    public String generateCode() {
        return "(-(" + getInput().generateCode() + "))";
    }
}
