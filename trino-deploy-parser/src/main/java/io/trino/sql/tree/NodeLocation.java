package io.trino.sql.tree;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * SQL语法树：解析节点位置信息
 *
 * @author ZhangXueJun
 * @Date 2023年04月03日
 */
public class NodeLocation {

    private final int line;
    private final int column;

    public NodeLocation(int line, int column)
    {
        checkArgument(line >= 1, "line must be at least one, got: %s", line);
        checkArgument(column >= 1, "column must be at least one, got: %s", column);

        this.line = line;
        this.column = column;
    }

    public int getLineNumber()
    {
        return line;
    }

    public int getColumnNumber()
    {
        return column;
    }

    @Override
    public String toString()
    {
        return line + ":" + column;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NodeLocation that = (NodeLocation) o;
        return line == that.line &&
                column == that.column;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(line, column);
    }
}
