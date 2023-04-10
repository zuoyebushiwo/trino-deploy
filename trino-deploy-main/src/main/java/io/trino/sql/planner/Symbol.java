package io.trino.sql.planner;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.trino.sql.tree.Expression;
import io.trino.sql.tree.SymbolReference;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

/**
 * @author ZhangXueJun
 * @Date 2023年04月03日
 */
public class Symbol implements Comparable<Symbol> {

    private final String name;


    public static Symbol from(Expression expression) {
        checkArgument(expression instanceof SymbolReference, "Unexpected expression: %s", expression);
        return new Symbol(((SymbolReference) expression).getName());
    }

    @JsonCreator
    public Symbol(String name) {
        requireNonNull(name, "name is null");
        this.name = name;
    }

    @JsonValue
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Symbol symbol = (Symbol) o;

        return name.equals(symbol.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public int compareTo(Symbol o) {
        return name.compareTo(o.name);
    }
}
