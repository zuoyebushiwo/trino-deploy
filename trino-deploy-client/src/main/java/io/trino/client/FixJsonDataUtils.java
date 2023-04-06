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
package io.trino.client;

import com.google.common.collect.ImmutableList;
import io.trino.client.ClientTypeSignatureParameter.ParameterKind;

import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static io.trino.client.ClientStandardTypes.*;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

final class FixJsonDataUtils
{
    private FixJsonDataUtils() {}

    public static Iterable<List<Object>> fixData(List<Column> columns, Iterable<List<Object>> data)
    {
        if (data == null) {
            return null;
        }
        requireNonNull(columns, "columns is null");
        List<ClientTypeSignature> signatures = columns.stream()
                .map(Column::getTypeSignature)
                .collect(toList());
        ImmutableList.Builder<List<Object>> rows = ImmutableList.builder();
        for (List<Object> row : data) {
            checkArgument(row.size() == columns.size(), "row/column size mismatch");
            List<Object> newRow = new ArrayList<>();
            for (int i = 0; i < row.size(); i++) {
                newRow.add(fixValue(signatures.get(i), row.get(i)));
            }
            rows.add(unmodifiableList(newRow)); // allow nulls in list
        }
        return rows.build();
    }

    /**
     * Force values coming from Jackson to have the expected object type.
     */
    private static Object fixValue(ClientTypeSignature signature, Object value)
    {
        if (value == null) {
            return null;
        }

        if (signature.getRawType().equals(ARRAY)) {
            List<Object> fixedValue = new ArrayList<>();
            for (Object object : List.class.cast(value)) {
                fixedValue.add(fixValue(signature.getArgumentsAsTypeSignatures().get(0), object));
            }
            return fixedValue;
        }
        if (signature.getRawType().equals(MAP)) {
            ClientTypeSignature keySignature = signature.getArgumentsAsTypeSignatures().get(0);
            ClientTypeSignature valueSignature = signature.getArgumentsAsTypeSignatures().get(1);
            Map<Object, Object> fixedValue = new HashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                fixedValue.put(fixValue(keySignature, entry.getKey()), fixValue(valueSignature, entry.getValue()));
            }
            return fixedValue;
        }
        if (signature.getRawType().equals(ROW)) {
            Row.Builder row = Row.builder();
            List<?> listValue = ((List<?>) value);
            checkArgument(listValue.size() == signature.getArguments().size(), "Mismatched data values and row type");
            for (int i = 0; i < listValue.size(); i++) {
                ClientTypeSignatureParameter parameter = signature.getArguments().get(i);
                checkArgument(
                        parameter.getKind() == ParameterKind.NAMED_TYPE,
                        "Unexpected parameter [%s] for row type",
                        parameter);
                NamedClientTypeSignature namedTypeSignature = parameter.getNamedTypeSignature();
                Object fixedValue = fixValue(namedTypeSignature.getTypeSignature(), listValue.get(i));
                if (namedTypeSignature.getName().isPresent()) {
                    row.addField(namedTypeSignature.getName().get(), fixedValue);
                }
                else {
                    row.addUnnamedField(fixedValue);
                }
            }
            return row.build();
        }
        switch (signature.getRawType()) {
            case BIGINT:
                if (value instanceof String) {
                    return Long.parseLong((String) value);
                }
                return ((Number) value).longValue();
            case INTEGER:
                if (value instanceof String) {
                    return Integer.parseInt((String) value);
                }
                return ((Number) value).intValue();
            case SMALLINT:
                if (value instanceof String) {
                    return Short.parseShort((String) value);
                }
                return ((Number) value).shortValue();
            case TINYINT:
                if (value instanceof String) {
                    return Byte.parseByte((String) value);
                }
                return ((Number) value).byteValue();
            case DOUBLE:
                if (value instanceof String) {
                    return Double.parseDouble((String) value);
                }
                return ((Number) value).doubleValue();
            case REAL:
                if (value instanceof String) {
                    return Float.parseFloat((String) value);
                }
                return ((Number) value).floatValue();
            case BOOLEAN:
                if (value instanceof String) {
                    return Boolean.parseBoolean((String) value);
                }
                return Boolean.class.cast(value);
            case VARCHAR:
            case JSON:
            case TIME:
            case TIME_WITH_TIME_ZONE:
            case TIMESTAMP:
            case TIMESTAMP_WITH_TIME_ZONE:
            case DATE:
            case INTERVAL_YEAR_TO_MONTH:
            case INTERVAL_DAY_TO_SECOND:
            case IPADDRESS:
            case UUID:
            case DECIMAL:
            case CHAR:
            case GEOMETRY:
            case SPHERICAL_GEOGRAPHY:
                return String.class.cast(value);
            case BING_TILE:
                // Bing tiles are serialized as strings when used as map keys,
                // they are serialized as json otherwise (value will be a LinkedHashMap).
                return value;
            default:
                // for now we assume that only the explicit types above are passed
                // as a plain text and everything else is base64 encoded binary
                if (value instanceof String) {
                    return Base64.getDecoder().decode((String) value);
                }
                return value;
        }
    }
}
