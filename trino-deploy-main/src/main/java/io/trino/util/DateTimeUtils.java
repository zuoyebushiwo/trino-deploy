/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.util;

import io.trino.client.IntervalDayTime;
import io.trino.client.IntervalYearMonth;
import io.trino.spi.TrinoException;
import io.trino.spi.type.TimeZoneKey;
import io.trino.sql.tree.IntervalLiteral.IntervalField;
import org.joda.time.*;
import org.joda.time.chrono.ISOChronology;
import org.joda.time.format.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static io.trino.spi.StandardErrorCode.INVALID_FUNCTION_ARGUMENT;
import static io.trino.spi.type.DateTimeEncoding.unpackMillisUtc;
import static io.trino.util.DateTimeZoneIndex.*;
import static java.lang.Math.toIntExact;
import static java.lang.String.format;

public final class DateTimeUtils
{
    private DateTimeUtils() {}

    private static final DateTimeFormatter DATE_FORMATTER = ISODateTimeFormat.date().withZoneUTC();

    public static int parseDate(String value)
    {
        return toIntExact(TimeUnit.MILLISECONDS.toDays(DATE_FORMATTER.parseMillis(value)));
    }

    public static String printDate(int days)
    {
        return DATE_FORMATTER.print(TimeUnit.DAYS.toMillis(days));
    }

    private static final DateTimeFormatter TIMESTAMP_WITH_TIME_ZONE_FORMATTER;
    private static final DateTimeFormatter TIMESTAMP_WITH_OR_WITHOUT_TIME_ZONE_FORMATTER;

    static {
        DateTimeParser[] timestampWithoutTimeZoneParser = {
                DateTimeFormat.forPattern("yyyyyy-M-d").getParser(),
                DateTimeFormat.forPattern("yyyyyy-M-d H:m").getParser(),
                DateTimeFormat.forPattern("yyyyyy-M-d H:m:s").getParser(),
                DateTimeFormat.forPattern("yyyyyy-M-d H:m:s.SSS").getParser()};

        DateTimeParser[] timestampWithTimeZoneParser = {
                DateTimeFormat.forPattern("yyyyyy-M-dZ").getParser(),
                DateTimeFormat.forPattern("yyyyyy-M-d Z").getParser(),
                DateTimeFormat.forPattern("yyyyyy-M-d H:mZ").getParser(),
                DateTimeFormat.forPattern("yyyyyy-M-d H:m Z").getParser(),
                DateTimeFormat.forPattern("yyyyyy-M-d H:m:sZ").getParser(),
                DateTimeFormat.forPattern("yyyyyy-M-d H:m:s Z").getParser(),
                DateTimeFormat.forPattern("yyyyyy-M-d H:m:s.SSSZ").getParser(),
                DateTimeFormat.forPattern("yyyyyy-M-d H:m:s.SSS Z").getParser(),
                DateTimeFormat.forPattern("yyyyyy-M-dZZZ").getParser(),
                DateTimeFormat.forPattern("yyyyyy-M-d ZZZ").getParser(),
                DateTimeFormat.forPattern("yyyyyy-M-d H:mZZZ").getParser(),
                DateTimeFormat.forPattern("yyyyyy-M-d H:m ZZZ").getParser(),
                DateTimeFormat.forPattern("yyyyyy-M-d H:m:sZZZ").getParser(),
                DateTimeFormat.forPattern("yyyyyy-M-d H:m:s ZZZ").getParser(),
                DateTimeFormat.forPattern("yyyyyy-M-d H:m:s.SSSZZZ").getParser(),
                DateTimeFormat.forPattern("yyyyyy-M-d H:m:s.SSS ZZZ").getParser()};

        DateTimePrinter timestampWithTimeZonePrinter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSS ZZZ").getPrinter();
        TIMESTAMP_WITH_TIME_ZONE_FORMATTER = new DateTimeFormatterBuilder()
                .append(timestampWithTimeZonePrinter, timestampWithTimeZoneParser)
                .toFormatter()
                .withOffsetParsed();

        DateTimeParser[] timestampWithOrWithoutTimeZoneParser = Stream.concat(Stream.of(timestampWithoutTimeZoneParser), Stream.of(timestampWithTimeZoneParser))
                .toArray(DateTimeParser[]::new);
        TIMESTAMP_WITH_OR_WITHOUT_TIME_ZONE_FORMATTER = new DateTimeFormatterBuilder()
                .append(timestampWithTimeZonePrinter, timestampWithOrWithoutTimeZoneParser)
                .toFormatter()
                .withOffsetParsed();
    }

    /**
     * Parse a string (optionally containing a zone) as a value of TIMESTAMP WITH TIME ZONE type.
     * If the string doesn't specify a zone, it is interpreted in {@code timeZoneKey} zone.
     * <p>
     * For example: {@code "2000-01-01 01:23:00"} is parsed to TIMESTAMP WITH TIME ZONE
     * {@code 2000-01-01T01:23:00 <provided zone>} and {@code "2000-01-01 01:23:00 +01:23"}
     * is parsed to TIMESTAMP WITH TIME ZONE {@code 2000-01-01T01:23:00.000+01:23}.
     *
     * @return stack representation of TIMESTAMP WITH TIME ZONE type
     */
    public static long convertToTimestampWithTimeZone(TimeZoneKey timeZoneKey, String timestampWithTimeZone)
    {
        DateTime dateTime = TIMESTAMP_WITH_OR_WITHOUT_TIME_ZONE_FORMATTER.withChronology(getChronology(timeZoneKey)).withOffsetParsed().parseDateTime(timestampWithTimeZone);
        return packDateTimeWithZone(dateTime);
    }

    public static String printTimestampWithTimeZone(long timestampWithTimeZone)
    {
        ISOChronology chronology = unpackChronology(timestampWithTimeZone);
        long millis = unpackMillisUtc(timestampWithTimeZone);
        return TIMESTAMP_WITH_TIME_ZONE_FORMATTER.withChronology(chronology).print(millis);
    }

    private static final DateTimeFormatter TIME_FORMATTER;
    private static final DateTimeFormatter TIME_WITH_TIME_ZONE_FORMATTER;

    static {
        DateTimeParser[] timeWithoutTimeZoneParser = {
                DateTimeFormat.forPattern("H:m").getParser(),
                DateTimeFormat.forPattern("H:m:s").getParser(),
                DateTimeFormat.forPattern("H:m:s.SSS").getParser()};
        DateTimePrinter timeWithoutTimeZonePrinter = DateTimeFormat.forPattern("HH:mm:ss.SSS").getPrinter();
        TIME_FORMATTER = new DateTimeFormatterBuilder().append(timeWithoutTimeZonePrinter, timeWithoutTimeZoneParser).toFormatter().withZoneUTC();

        DateTimeParser[] timeWithTimeZoneParser = {
                DateTimeFormat.forPattern("H:mZ").getParser(),
                DateTimeFormat.forPattern("H:m Z").getParser(),
                DateTimeFormat.forPattern("H:m:sZ").getParser(),
                DateTimeFormat.forPattern("H:m:s Z").getParser(),
                DateTimeFormat.forPattern("H:m:s.SSSZ").getParser(),
                DateTimeFormat.forPattern("H:m:s.SSS Z").getParser(),
                DateTimeFormat.forPattern("H:mZZZ").getParser(),
                DateTimeFormat.forPattern("H:m ZZZ").getParser(),
                DateTimeFormat.forPattern("H:m:sZZZ").getParser(),
                DateTimeFormat.forPattern("H:m:s ZZZ").getParser(),
                DateTimeFormat.forPattern("H:m:s.SSSZZZ").getParser(),
                DateTimeFormat.forPattern("H:m:s.SSS ZZZ").getParser()};
        DateTimePrinter timeWithTimeZonePrinter = DateTimeFormat.forPattern("HH:mm:ss.SSS ZZZ").getPrinter();
        TIME_WITH_TIME_ZONE_FORMATTER = new DateTimeFormatterBuilder().append(timeWithTimeZonePrinter, timeWithTimeZoneParser).toFormatter().withOffsetParsed();
    }

    /**
     * Parse a string (without a zone) as a value of TIME type, interpreted in {@code timeZoneKey} zone.
     *
     * @return stack representation of legacy TIME type
     * @deprecated applicable in legacy timestamp semantics only
     */
    @Deprecated
    public static long parseLegacyTime(TimeZoneKey timeZoneKey, String value)
    {
        return TIME_FORMATTER.withZone(getDateTimeZone(timeZoneKey)).parseMillis(value);
    }

    public static String printTimeWithTimeZone(long timeWithTimeZone)
    {
        DateTimeZone timeZone = unpackDateTimeZone(timeWithTimeZone);
        long millis = unpackMillisUtc(timeWithTimeZone);
        return TIME_WITH_TIME_ZONE_FORMATTER.withZone(timeZone).print(millis);
    }

    public static String printTimeWithoutTimeZone(long value)
    {
        return TIME_FORMATTER.print(value);
    }

    /**
     * @deprecated applicable in legacy timestamp semantics only
     */
    @Deprecated
    public static String printTimeWithoutTimeZone(TimeZoneKey timeZoneKey, long value)
    {
        return TIME_FORMATTER.withZone(getDateTimeZone(timeZoneKey)).print(value);
    }

    private static final int YEAR_FIELD = 0;
    private static final int MONTH_FIELD = 1;
    private static final int DAY_FIELD = 3;
    private static final int HOUR_FIELD = 4;
    private static final int MINUTE_FIELD = 5;
    private static final int SECOND_FIELD = 6;
    private static final int MILLIS_FIELD = 7;

    private static final PeriodFormatter INTERVAL_DAY_SECOND_FORMATTER = cretePeriodFormatter(IntervalField.DAY, IntervalField.SECOND);
    private static final PeriodFormatter INTERVAL_DAY_MINUTE_FORMATTER = cretePeriodFormatter(IntervalField.DAY, IntervalField.MINUTE);
    private static final PeriodFormatter INTERVAL_DAY_HOUR_FORMATTER = cretePeriodFormatter(IntervalField.DAY, IntervalField.HOUR);
    private static final PeriodFormatter INTERVAL_DAY_FORMATTER = cretePeriodFormatter(IntervalField.DAY, IntervalField.DAY);

    private static final PeriodFormatter INTERVAL_HOUR_SECOND_FORMATTER = cretePeriodFormatter(IntervalField.HOUR, IntervalField.SECOND);
    private static final PeriodFormatter INTERVAL_HOUR_MINUTE_FORMATTER = cretePeriodFormatter(IntervalField.HOUR, IntervalField.MINUTE);
    private static final PeriodFormatter INTERVAL_HOUR_FORMATTER = cretePeriodFormatter(IntervalField.HOUR, IntervalField.HOUR);

    private static final PeriodFormatter INTERVAL_MINUTE_SECOND_FORMATTER = cretePeriodFormatter(IntervalField.MINUTE, IntervalField.SECOND);
    private static final PeriodFormatter INTERVAL_MINUTE_FORMATTER = cretePeriodFormatter(IntervalField.MINUTE, IntervalField.MINUTE);

    private static final PeriodFormatter INTERVAL_SECOND_FORMATTER = cretePeriodFormatter(IntervalField.SECOND, IntervalField.SECOND);

    private static final PeriodFormatter INTERVAL_YEAR_MONTH_FORMATTER = cretePeriodFormatter(IntervalField.YEAR, IntervalField.MONTH);
    private static final PeriodFormatter INTERVAL_YEAR_FORMATTER = cretePeriodFormatter(IntervalField.YEAR, IntervalField.YEAR);

    private static final PeriodFormatter INTERVAL_MONTH_FORMATTER = cretePeriodFormatter(IntervalField.MONTH, IntervalField.MONTH);

    public static long parseDayTimeInterval(String value, IntervalField startField, Optional<IntervalField> endField)
    {
        IntervalField end = endField.orElse(startField);

        if (startField == IntervalField.DAY && end == IntervalField.SECOND) {
            return parsePeriodMillis(INTERVAL_DAY_SECOND_FORMATTER, value, startField, end);
        }
        if (startField == IntervalField.DAY && end == IntervalField.MINUTE) {
            return parsePeriodMillis(INTERVAL_DAY_MINUTE_FORMATTER, value, startField, end);
        }
        if (startField == IntervalField.DAY && end == IntervalField.HOUR) {
            return parsePeriodMillis(INTERVAL_DAY_HOUR_FORMATTER, value, startField, end);
        }
        if (startField == IntervalField.DAY && end == IntervalField.DAY) {
            return parsePeriodMillis(INTERVAL_DAY_FORMATTER, value, startField, end);
        }

        if (startField == IntervalField.HOUR && end == IntervalField.SECOND) {
            return parsePeriodMillis(INTERVAL_HOUR_SECOND_FORMATTER, value, startField, end);
        }
        if (startField == IntervalField.HOUR && end == IntervalField.MINUTE) {
            return parsePeriodMillis(INTERVAL_HOUR_MINUTE_FORMATTER, value, startField, end);
        }
        if (startField == IntervalField.HOUR && end == IntervalField.HOUR) {
            return parsePeriodMillis(INTERVAL_HOUR_FORMATTER, value, startField, end);
        }

        if (startField == IntervalField.MINUTE && end == IntervalField.SECOND) {
            return parsePeriodMillis(INTERVAL_MINUTE_SECOND_FORMATTER, value, startField, end);
        }
        if (startField == IntervalField.MINUTE && end == IntervalField.MINUTE) {
            return parsePeriodMillis(INTERVAL_MINUTE_FORMATTER, value, startField, end);
        }

        if (startField == IntervalField.SECOND && end == IntervalField.SECOND) {
            return parsePeriodMillis(INTERVAL_SECOND_FORMATTER, value, startField, end);
        }

        throw new IllegalArgumentException("Invalid day second interval qualifier: " + startField + " to " + end);
    }

    public static long parsePeriodMillis(PeriodFormatter periodFormatter, String value, IntervalField startField, IntervalField endField)
    {
        try {
            Period period = parsePeriod(periodFormatter, value);
            return IntervalDayTime.toMillis(
                    period.getValue(DAY_FIELD),
                    period.getValue(HOUR_FIELD),
                    period.getValue(MINUTE_FIELD),
                    period.getValue(SECOND_FIELD),
                    period.getValue(MILLIS_FIELD));
        }
        catch (IllegalArgumentException e) {
            throw invalidInterval(e, value, startField, endField);
        }
    }

    public static long parseYearMonthInterval(String value, IntervalField startField, Optional<IntervalField> endField)
    {
        IntervalField end = endField.orElse(startField);

        if (startField == IntervalField.YEAR && end == IntervalField.MONTH) {
            return parsePeriodMonths(value, INTERVAL_YEAR_MONTH_FORMATTER, startField, end);
        }
        if (startField == IntervalField.YEAR && end == IntervalField.YEAR) {
            return parsePeriodMonths(value, INTERVAL_YEAR_FORMATTER, startField, end);
        }

        if (startField == IntervalField.MONTH && end == IntervalField.MONTH) {
            return parsePeriodMonths(value, INTERVAL_MONTH_FORMATTER, startField, end);
        }

        throw new IllegalArgumentException("Invalid year month interval qualifier: " + startField + " to " + end);
    }

    private static long parsePeriodMonths(String value, PeriodFormatter periodFormatter, IntervalField startField, IntervalField endField)
    {
        try {
            Period period = parsePeriod(periodFormatter, value);
            return IntervalYearMonth.toMonths(
                    period.getValue(YEAR_FIELD),
                    period.getValue(MONTH_FIELD));
        }
        catch (IllegalArgumentException e) {
            throw invalidInterval(e, value, startField, endField);
        }
    }

    private static Period parsePeriod(PeriodFormatter periodFormatter, String value)
    {
        boolean negative = value.startsWith("-");
        if (negative) {
            value = value.substring(1);
        }

        Period period = periodFormatter.parsePeriod(value);
        for (DurationFieldType type : period.getFieldTypes()) {
            checkArgument(period.get(type) >= 0, "Period field %s is negative", type);
        }

        if (negative) {
            period = period.negated();
        }
        return period;
    }

    private static TrinoException invalidInterval(Throwable throwable, String value, IntervalField startField, IntervalField endField)
    {
        String message;
        if (startField == endField) {
            message = format("Invalid INTERVAL %s value: %s", startField, value);
        }
        else {
            message = format("Invalid INTERVAL %s TO %s value: %s", startField, endField, value);
        }
        return new TrinoException(INVALID_FUNCTION_ARGUMENT, message, throwable);
    }

    private static PeriodFormatter cretePeriodFormatter(IntervalField startField, IntervalField endField)
    {
        if (endField == null) {
            endField = startField;
        }

        List<PeriodParser> parsers = new ArrayList<>();

        PeriodFormatterBuilder builder = new PeriodFormatterBuilder();
        switch (startField) {
            case YEAR:
                builder.appendYears();
                parsers.add(builder.toParser());
                if (endField == IntervalField.YEAR) {
                    break;
                }
                builder.appendLiteral("-");
                // fall through

            case MONTH:
                builder.appendMonths();
                parsers.add(builder.toParser());
                if (endField != IntervalField.MONTH) {
                    throw new IllegalArgumentException("Invalid interval qualifier: " + startField + " to " + endField);
                }
                break;

            case DAY:
                builder.appendDays();
                parsers.add(builder.toParser());
                if (endField == IntervalField.DAY) {
                    break;
                }
                builder.appendLiteral(" ");
                // fall through

            case HOUR:
                builder.appendHours();
                parsers.add(builder.toParser());
                if (endField == IntervalField.HOUR) {
                    break;
                }
                builder.appendLiteral(":");
                // fall through

            case MINUTE:
                builder.appendMinutes();
                parsers.add(builder.toParser());
                if (endField == IntervalField.MINUTE) {
                    break;
                }
                builder.appendLiteral(":");
                // fall through

            case SECOND:
                builder.appendSecondsWithOptionalMillis();
                parsers.add(builder.toParser());
                break;
        }

        return new PeriodFormatter(builder.toPrinter(), new OrderedPeriodParser(parsers));
    }

    private static class OrderedPeriodParser
            implements PeriodParser
    {
        private final List<PeriodParser> parsers;

        private OrderedPeriodParser(List<PeriodParser> parsers)
        {
            this.parsers = parsers;
        }

        @Override
        public int parseInto(ReadWritablePeriod period, String text, int position, Locale locale)
        {
            int bestValidPos = position;
            ReadWritablePeriod bestValidPeriod = null;

            int bestInvalidPos = position;

            for (PeriodParser parser : parsers) {
                ReadWritablePeriod parsedPeriod = new MutablePeriod();
                int parsePos = parser.parseInto(parsedPeriod, text, position, locale);
                if (parsePos >= position) {
                    if (parsePos > bestValidPos) {
                        bestValidPos = parsePos;
                        bestValidPeriod = parsedPeriod;
                        if (parsePos >= text.length()) {
                            break;
                        }
                    }
                }
                else if (parsePos < 0) {
                    parsePos = ~parsePos;
                    if (parsePos > bestInvalidPos) {
                        bestInvalidPos = parsePos;
                    }
                }
            }

            if (bestValidPos > position || (bestValidPos == position)) {
                // Restore the state to the best valid parse.
                if (bestValidPeriod != null) {
                    period.setPeriod(bestValidPeriod);
                }
                return bestValidPos;
            }

            return ~bestInvalidPos;
        }
    }
}
