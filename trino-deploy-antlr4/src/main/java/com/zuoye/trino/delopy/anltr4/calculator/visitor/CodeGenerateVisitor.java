package com.zuoye.trino.delopy.anltr4.calculator.visitor;

import com.zuoye.trino.delopy.anltr4.calculator.node.AlgebraNode;
import com.zuoye.trino.delopy.anltr4.calculator.node.MinusNode;
import com.zuoye.trino.delopy.anltr4.calculator.node.PlusNode;
import com.zuoye.trino.delopy.anltr4.calculator.parse.CalculatorBaseVisitor;
import com.zuoye.trino.delopy.anltr4.calculator.parse.CalculatorParser;
import com.zuoye.trino.delopy.anltr4.calculator.type.DataType;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Map;

/**
 * @author ZuoYe
 * @Date 2023年04月02日
 */
public class CodeGenerateVisitor extends CalculatorBaseVisitor<AlgebraNode> {

    private final Map<String, DataType> variableTypes;

    public CodeGenerateVisitor(Map<String, DataType> variableTypes) {
        this.variableTypes = variableTypes;
    }

    @Override
    public AlgebraNode visitCalculate(CalculatorParser.CalculateContext ctx) {
        return visit(ctx.plusOrMinus());
    }

    @Override
    public AlgebraNode visitPlus(CalculatorParser.PlusContext ctx) {
        return new PlusNode(visit(ctx.plusOrMinus()), visit(ctx.multOrDiv()));
    }

    @Override
    public AlgebraNode visitMinus(CalculatorParser.MinusContext ctx) {
        return new MinusNode(visit(ctx.plusOrMinus()), visit(ctx.multOrDiv()));
    }

    @Override
    public AlgebraNode visitMultiplication(CalculatorParser.MultiplicationContext ctx) {
        return super.visitMultiplication(ctx);
    }
}
