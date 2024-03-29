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

import io.airlift.slice.Slice;
import io.airlift.slice.Slices;
import io.airlift.slice.XxHash64;
import io.trino.spi.TrinoException;
import io.trino.spi.block.Block;
import io.trino.spi.block.BlockBuilder;
import io.trino.spi.block.BlockBuilderStatus;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.function.BlockIndex;
import io.trino.spi.function.BlockPosition;
import io.trino.spi.function.ScalarOperator;

import java.util.Objects;

import static io.airlift.slice.SliceUtf8.countCodePoints;
import static io.trino.spi.StandardErrorCode.INVALID_FUNCTION_ARGUMENT;
import static io.trino.spi.function.OperatorType.*;
import static io.trino.spi.type.Chars.compareChars;
import static io.trino.spi.type.Chars.padSpaces;
import static io.trino.spi.type.Slices.sliceRepresentation;
import static io.trino.spi.type.TypeOperatorDeclaration.extractOperatorDeclaration;
import static java.lang.String.format;
import static java.lang.invoke.MethodHandles.lookup;
import static java.util.Collections.singletonList;

public final class CharType
        extends AbstractVariableWidthType
{
    private static final TypeOperatorDeclaration TYPE_OPERATOR_DECLARATION = extractOperatorDeclaration(CharType.class, lookup(), Slice.class);

    public static final int MAX_LENGTH = 65_536;

    private final int length;

    public static CharType createCharType(long length)
    {
        return new CharType(length);
    }

    private CharType(long length)
    {
        super(
                new TypeSignature(
                        StandardTypes.CHAR,
                        singletonList(TypeSignatureParameter.numericParameter(length))),
                Slice.class);

        if (length < 0 || length > MAX_LENGTH) {
            throw new TrinoException(INVALID_FUNCTION_ARGUMENT, format("CHAR length must be in range [0, %s], got %s", MAX_LENGTH, length));
        }
        this.length = (int) length;
    }

    public int getLength()
    {
        return length;
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

        Slice slice = block.getSlice(position, 0, block.getSliceLength(position));
        if (slice.length() > 0) {
            if (countCodePoints(slice) > length) {
                throw new IllegalArgumentException(format("Character count exceeds length limit %s: %s", length, sliceRepresentation(slice)));
            }
            if (slice.getByte(slice.length() - 1) == ' ') {
                throw new IllegalArgumentException(format("Value representation has a trailing space: %s", sliceRepresentation(slice)));
            }
        }

        return padSpaces(slice, length).toStringUtf8();
    }

    @Override
    public BlockBuilder createBlockBuilder(BlockBuilderStatus blockBuilderStatus, int expectedEntries)
    {
        // If bound on length of char is smaller than EXPECTED_BYTES_PER_ENTRY, use that as expectedBytesPerEntry
        // The data can take up to 4 bytes per character due to UTF-8 encoding, but we assume it is ASCII and only needs one byte.
        return createBlockBuilder(blockBuilderStatus, expectedEntries, Math.min(length, EXPECTED_BYTES_PER_ENTRY));
    }

    @Override
    public void appendTo(Block block, int position, BlockBuilder blockBuilder)
    {
        if (block.isNull(position)) {
            blockBuilder.appendNull();
        }
        else {
            block.writeBytesTo(position, 0, block.getSliceLength(position), blockBuilder);
            blockBuilder.closeEntry();
        }
    }

    @Override
    public Slice getSlice(Block block, int position)
    {
        return block.getSlice(position, 0, block.getSliceLength(position));
    }

    public void writeString(BlockBuilder blockBuilder, String value)
    {
        writeSlice(blockBuilder, Slices.utf8Slice(value));
    }

    @Override
    public void writeSlice(BlockBuilder blockBuilder, Slice value)
    {
        writeSlice(blockBuilder, value, 0, value.length());
    }

    @Override
    public void writeSlice(BlockBuilder blockBuilder, Slice value, int offset, int length)
    {
        if (length > 0 && value.getByte(offset + length - 1) == ' ') {
            throw new IllegalArgumentException("Slice representing Char should not have trailing spaces");
        }
        blockBuilder.writeBytes(value, offset, length).closeEntry();
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

        CharType other = (CharType) o;

        return Objects.equals(this.length, other.length);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(length);
    }

    @ScalarOperator(EQUAL)
    private static boolean equalOperator(Slice left, Slice right)
    {
        return left.equals(right);
    }

    @ScalarOperator(EQUAL)
    private static boolean equalOperator(@BlockPosition Block leftBlock, @BlockIndex int leftPosition, @BlockPosition Block rightBlock, @BlockIndex int rightPosition)
    {
        int leftLength = leftBlock.getSliceLength(leftPosition);
        int rightLength = rightBlock.getSliceLength(rightPosition);
        if (leftLength != rightLength) {
            return false;
        }
        return leftBlock.equals(leftPosition, 0, rightBlock, rightPosition, 0, leftLength);
    }

    @ScalarOperator(XX_HASH_64)
    private static long xxHash64Operator(Slice value)
    {
        return XxHash64.hash(value);
    }

    @ScalarOperator(XX_HASH_64)
    private static long xxHash64Operator(@BlockPosition Block block, @BlockIndex int position)
    {
        return block.hash(position, 0, block.getSliceLength(position));
    }

    @ScalarOperator(COMPARISON_UNORDERED_LAST)
    private static long comparisonOperator(Slice left, Slice right)
    {
        return compareChars(left, right);
    }
}
