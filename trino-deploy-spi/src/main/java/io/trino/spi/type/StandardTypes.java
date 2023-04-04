package io.trino.spi.type;

/**
 * @author ZhangXueJun
 * @Date 2023年04月04日
 */
public final class StandardTypes {
    public static final String BIGINT = "bigint";
    public static final String INTEGER = "integer";
    public static final String SMALLINT = "smallint";
    public static final String TINYINT = "tinyint";
    public static final String BOOLEAN = "boolean";
    public static final String DATE = "date";
    public static final String DECIMAL = "decimal";
    public static final String REAL = "real";
    public static final String DOUBLE = "double";
    public static final String HYPER_LOG_LOG = "HyperLogLog";
    public static final String QDIGEST = "qdigest";
    public static final String TDIGEST = "tdigest";
    public static final String P4_HYPER_LOG_LOG = "P4HyperLogLog";
    public static final String INTERVAL_DAY_TO_SECOND = "interval day to second";
    public static final String INTERVAL_YEAR_TO_MONTH = "interval year to month";
    public static final String TIMESTAMP = "timestamp";
    public static final String TIMESTAMP_WITH_TIME_ZONE = "timestamp with time zone";
    public static final String TIME = "time";
    public static final String TIME_WITH_TIME_ZONE = "time with time zone";
    public static final String VARBINARY = "varbinary";
    public static final String VARCHAR = "varchar";
    public static final String CHAR = "char";
    public static final String ROW = "row";
    public static final String ARRAY = "array";
    public static final String MAP = "map";
    public static final String JSON = "json";
    public static final String IPADDRESS = "ipaddress";
    public static final String GEOMETRY = "Geometry";
    public static final String UUID = "uuid";

    private StandardTypes() {}
}
