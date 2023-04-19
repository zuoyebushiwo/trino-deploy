package io.trino.sql.analyzer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.trino.metadata.QualifiedObjectName;
import io.trino.spi.eventlistener.ColumnDetail;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * @author ZhangXueJun
 * @Date 2023年04月11日
 */
public class Analysis {

    public static class SourceColumn
    {
        private final QualifiedObjectName tableName;
        private final String columnName;

        @JsonCreator
        public SourceColumn(@JsonProperty("tableName") QualifiedObjectName tableName, @JsonProperty("columnName") String columnName)
        {
            this.tableName = requireNonNull(tableName, "tableName is null");
            this.columnName = requireNonNull(columnName, "columnName is null");
        }

        @JsonProperty
        public QualifiedObjectName getTableName()
        {
            return tableName;
        }

        @JsonProperty
        public String getColumnName()
        {
            return columnName;
        }

        public ColumnDetail getColumnDetail()
        {
            return new ColumnDetail(tableName.getCatalogName(), tableName.getSchemaName(), tableName.getObjectName(), columnName);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(tableName, columnName);
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj == this) {
                return true;
            }
            if ((obj == null) || (getClass() != obj.getClass())) {
                return false;
            }
            SourceColumn entry = (SourceColumn) obj;
            return Objects.equals(tableName, entry.tableName) &&
                    Objects.equals(columnName, entry.columnName);
        }
    }
}
