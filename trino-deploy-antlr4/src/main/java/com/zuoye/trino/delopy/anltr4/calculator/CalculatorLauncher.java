package com.zuoye.trino.delopy.anltr4.calculator;

import com.google.common.collect.ImmutableMap;
import com.zuoye.trino.delopy.anltr4.calculator.type.DataType;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Map;

/**
 * 计算器：代码自动生成
 *
 * @author ZuoYe
 * @Date 2023年04月01日
 */
public class CalculatorLauncher {

    public static void main(String[] args) {
        String expr = "a + 2*3 - 2/x + log(x+1)";

        Map<String, DataType> dataTypes = ImmutableMap.of(
                "a", DataType.LONG,
                "x", DataType.DOUBLE);

        Map<String, Object> dataValues = ImmutableMap.of(
                "a", 10L,
                "x", 3.1415);

    }
}
