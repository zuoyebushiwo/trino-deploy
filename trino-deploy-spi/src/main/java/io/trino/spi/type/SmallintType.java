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
package io.trino.spi.type;

import io.airlift.slice.XxHash64;
import io.trino.spi.TrinoException;
import io.trino.spi.block.*;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.function.ScalarOperator;

import java.util.Optional;

import static io.trino.spi.StandardErrorCode.GENERIC_INTERNAL_ERROR;
import static io.trino.spi.function.OperatorType.*;
import static io.trino.spi.type.TypeOperatorDeclaration.extractOperatorDeclaration;
import static java.lang.String.format;
import static java.lang.invoke.MethodHandles.lookup;

public final class SmallintType
        extends AbstractType
        implements FixedWidthType
{
    private static final TypeOperatorDeclaration TYPE_OPERATOR_DECLARATION = extractOperatorDeclaration(SmallintType.class, lookup(), long.class);

    public static final SmallintType SMALLINT = new SmallintType();

    private SmallintType()
    {
        super(new TypeSignature(StandardTypes.SMALLINT), long.class);
    }

    @Override
    public int getFixedSize()
    {
        return Short.BYTES;
    }

    @Override
    public BlockBuilder createBlockBuilder(BlockBuilderStatus blockBuilderStatus, int expectedEntries, int expectedBytesPerEntry)
    {
        int maxBlockSizeInBytes;
        if (blockBuilderStatus == null) {
            maxBlockSizeInBytes = PageBuilderStatus.DEFAULT_MAX_PAGE_SIZE_IN_BYTES;
        }
        else {
            maxBlockSizeInBytes = blockBuilderStatus.getMaxPageSizeInBytes();
        }
        return new ShortArrayBlockBuilder(
                blockBuilderStatus,
                Math.min(expectedEntries, maxBlockSizeInBytes / Short.BYTES));
    }

    @Override
    public BlockBuilder createBlockBuilder(BlockBuilderStatus blockBuilderStatus, int expectedEntries)
    {
        return createBlockBuilder(blockBuilderStatus, expectedEntries, Short.BYTES);
    }

    @Override
    public BlockBuilder createFixedSizeBlockBuilder(int positionCount)
    {
        return new ShortArrayBlockBuilder(null, positionCount);
    }

    @Override
    public boolean isComparable()
    {
        return true;
    }

    @Override
    public boolean isOrderable()
    {
        return true;
    }

    @Override
    public TypeOperatorDeclaration getTypeOperatorDeclaration(TypeOperators typeOperators)
    {
        return TYPE_OPERATOR_DECLARATION;
    }

    @Override
    public Object getObjectValue(ConnectorSession session, Block block, int position)
    {
        if (block.isNull(position)) {
            return null;
        }

        return block.getShort(position, 0);
    }

    @Override
    public Optional<Range> getRange()
    {
        return Optional.of(new Range((long) Short.MIN_VALUE, (long) Short.MAX_VALUE));
    }

    @Override
    public void appendTo(Block block, int position, BlockBuilder blockBuilder)
    {
        if (block.isNull(position)) {
            blockBuilder.appendNull();
        }
        else {
            blockBuilder.writeShort(block.getShort(position, 0)).closeEntry();
        }
    }

    @Override
    public long getLong(Block block, int position)
    {
        return (long) block.getShort(position, 0);
    }

    @Override
    public void writeLong(BlockBuilder blockBuilder, long value)
    {
        if (value > Short.MAX_VALUE) {
            throw new TrinoException(GENERIC_INTERNAL_ERROR, format("Value %d exceeds MAX_SHORT", value));
        }
        if (value < Short.MIN_VALUE) {
            throw new TrinoException(GENERIC_INTERNAL_ERROR, format("Value %d is less than MIN_SHORT", value));
        }

        blockBuilder.writeShort((int) value).closeEntry();
    }

    @Override
    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    public boolean equals(Object other)
    {
        return other == SMALLINT;
    }

    @Override
    public int hashCode()
    {
        return getClass().hashCode();
    }

    @ScalarOperator(EQUAL)
    private static boolean equalOperator(long left, long right)
    {
        return left == right;
    }

    @ScalarOperator(HASH_CODE)
    private static long hashCodeOperator(long value)
    {
        return AbstractLongType.hash((short) value);
    }

    @ScalarOperator(XX_HASH_64)
    private static long xxHash64Operator(long value)
    {
        return XxHash64.hash((short) value);
    }

    @ScalarOperator(COMPARISON_UNORDERED_LAST)
    private static long comparisonOperator(long left, long right)
    {
        return Short.compare((short) left, (short) right);
    }

    @ScalarOperator(LESS_THAN)
    private static boolean lessThanOperator(long left, long right)
    {
        return ((short) left) < ((short) right);
    }

    @ScalarOperator(LESS_THAN_OR_EQUAL)
    private static boolean lessThanOrEqualOperator(long left, long right)
    {
        return ((short) left) <= ((short) right);
    }
}
