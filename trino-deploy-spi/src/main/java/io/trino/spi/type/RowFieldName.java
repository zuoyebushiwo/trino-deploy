package io.trino.spi.type;

import javax.annotation.concurrent.Immutable;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * @author ZhangXueJun
 * @Date 2023年04月04日
 */
@Immutable
public final class RowFieldName {
    private final String name;

    public RowFieldName(String name)
    {
        this.name = requireNonNull(name, "name is null");
    }

    public String getName()
    {
        return name;
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

        RowFieldName other = (RowFieldName) o;

        return Objects.equals(this.name, other.name);
    }

    @Override
    public String toString()
    {
        return '"' + name.replace("\"", "\"\"") + '"';
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name);
    }
}
