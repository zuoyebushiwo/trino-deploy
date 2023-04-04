package io.trino.sql.parser;

import io.trino.sql.tree.Node;
import io.trino.sql.tree.PathSpecification;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.function.Function;

/**
 * @author ZhangXueJun
 * @Date 2023年04月04日
 */
public class SqlParser {
    public PathSpecification createPathSpecification(String expression)
    {
        return (PathSpecification) invokeParser("path specification", expression, SqlBaseParser::standalonePathSpecification, new ParsingOptions());
    }

    private Node invokeParser(String name, String sql, Function<SqlBaseParser, ParserRuleContext> parseFunction, ParsingOptions parsingOptions) {
        return null;
    }
}
