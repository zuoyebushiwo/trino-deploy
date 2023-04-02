package com.zuoye.trino.delopy.anltr4.calculator;

import com.google.common.collect.ImmutableMap;
import com.zuoye.trino.delopy.anltr4.calculator.node.AlgebraNode;
import com.zuoye.trino.delopy.anltr4.calculator.parse.CalculatorLexer;
import com.zuoye.trino.delopy.anltr4.calculator.parse.CalculatorParser;
import com.zuoye.trino.delopy.anltr4.calculator.type.DataType;
import com.zuoye.trino.delopy.anltr4.calculator.visitor.CodeGenerateVisitor;
import com.zuoye.trino.delopy.anltr4.calculator.visitor.DefaultCalculatorVisitor;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.codehaus.janino.ExpressionEvaluator;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * 计算器：代码自动生成
 *
 * @author ZuoYe
 * @Date 2023年04月01日
 */
public class CalculatorLauncher {

    public static void main(String[] args) throws Exception {
        String expr = "a + 2*3 - 2/x + log(x+1)";

        Map<String, DataType> dataTypes = ImmutableMap.of(
                "a", DataType.LONG,
                "x", DataType.DOUBLE);

        Map<String, Object> dataValues = ImmutableMap.of(
                "a", 10L,
                "x", 3.1415);

        CharStream input = CharStreams.fromString(expr);
        CalculatorLexer lexer = new CalculatorLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        CalculatorParser parser = new CalculatorParser(tokens);
        ParseTree root = parser.input();

        {
            Object result = solveByInterpreter(root, dataValues);
            System.out.println("Result: " + result + " (" + result.getClass() + ")");
        }

        {
            Object result = solveByCodeGen(root, dataTypes, dataValues);
            System.out.println("Result: " + result + " (" + result.getClass() + ")");
        }
    }

    private static Object solveByCodeGen(
            ParseTree root,
            Map<String, DataType> dataTypes,
            Map<String, Object> dataValues) throws Exception {
        /**
         * 第一步：Convert AST to AlgebraNode(validating)
         */
        CodeGenerateVisitor visitor = new CodeGenerateVisitor(dataTypes);
        AlgebraNode rootNode = visitor.visit(root);

        /**
         * 第二步：用AlgebraNode自动生成代码：janino
         */
        String code = rootNode.generateCode();
        System.out.println("Code Generated: " + code);

        /**
         * 第三部：根据参数执行
         */
        ExpressionEvaluator evaluator = new ExpressionEvaluator();
        final int numParameters = dataTypes.size();
        String[] parameterNames = new String[numParameters];
        Class[] parameterTypes = new Class[numParameters];
        int index = 0;
        for (Map.Entry<String, DataType> entry : dataTypes.entrySet()) {
            parameterNames[index] = entry.getKey();
            parameterTypes[index] = entry.getValue() == DataType.DOUBLE ? double.class : long.class;
            index++;
        }
        evaluator.setParameters(parameterNames, parameterTypes);
        evaluator.setExpressionType(rootNode.getType() == DataType.DOUBLE ? double.class : long.class);
        evaluator.cook(code);

        Object[] parameterValues = new Object[numParameters];
        for (int i = 0; i < parameterNames.length; i++) {
            parameterValues[i] = dataValues.get(parameterNames[i]);
        }
        return evaluator.evaluate(parameterValues);
    }

    private static Object solveByInterpreter(
            ParseTree root,
            Map<String, Object> dataValues) {
        DefaultCalculatorVisitor calcVisitor = new DefaultCalculatorVisitor(dataValues);
        return calcVisitor.visit(root);
    }
}
