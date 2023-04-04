package io.trino.spi.type;

import javax.annotation.concurrent.Immutable;
import java.util.Objects;
import java.util.Optional;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * @author ZhangXueJun
 * @Date 2023年04月04日
 */
@Immutable
public class NamedTypeSignature {
    private final Optional<RowFieldName> fieldName;
    private final TypeSignature typeSignature;

    public NamedTypeSignature(Optional<RowFieldName> fieldName, TypeSignature typeSignature)
    {
        this.fieldName = requireNonNull(fieldName, "fieldName is null");
        this.typeSignature = requireNonNull(typeSignature, "typeSignature is null");
    }

    public Optional<RowFieldName> getFieldName()
    {
        return fieldName;
    }

    public TypeSignature getTypeSignature()
    {
        return typeSignature;
    }

    public Optional<String> getName()
    {
        return getFieldName().map(RowFieldName::getName);
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

        NamedTypeSignature other = (NamedTypeSignature) o;

        return Objects.equals(this.fieldName, other.fieldName) &&
                Objects.equals(this.typeSignature, other.typeSignature);
    }

    @Override
    public String toString()
    {
        if (fieldName.isPresent()) {
            return format("\"%s\" %s", fieldName.get().getName().replace("\"", "\"\""), typeSignature);
        }
        return typeSignature.toString();
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(fieldName, typeSignature);
    }
}
