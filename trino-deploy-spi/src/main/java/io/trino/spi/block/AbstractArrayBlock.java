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
package io.trino.spi.block;

import javax.annotation.Nullable;
import java.util.List;

import static io.trino.spi.block.ArrayBlock.createArrayBlockInternal;
import static io.trino.spi.block.BlockUtil.*;
import static java.util.Collections.singletonList;

public abstract class AbstractArrayBlock
        implements Block
{
    @Override
    public final List<Block> getChildren()
    {
        return singletonList(getRawElementBlock());
    }

    protected abstract Block getRawElementBlock();

    protected abstract int[] getOffsets();

    protected abstract int getOffsetBase();

    /**
     * @return the underlying valueIsNull array, or null when all values are guaranteed to be non-null
     */
    @Nullable
    protected abstract boolean[] getValueIsNull();

    int getOffset(int position)
    {
        return getOffsets()[position + getOffsetBase()];
    }

    @Override
    public String getEncodingName()
    {
        return ArrayBlockEncoding.NAME;
    }

    @Override
    public Block copyPositions(int[] positions, int offset, int length)
    {
        checkArrayRange(positions, offset, length);

        int[] newOffsets = new int[length + 1];
        newOffsets[0] = 0;
        boolean[] newValueIsNull = getValueIsNull() == null ? null : new boolean[length];

        IntArrayList valuesPositions = new IntArrayList();
        for (int i = 0; i < length; i++) {
            int position = positions[offset + i];
            if (newValueIsNull != null && isNull(position)) {
                newValueIsNull[i] = true;
                newOffsets[i + 1] = newOffsets[i];
            }
            else {
                int valuesStartOffset = getOffset(position);
                int valuesEndOffset = getOffset(position + 1);
                int valuesLength = valuesEndOffset - valuesStartOffset;

                newOffsets[i + 1] = newOffsets[i] + valuesLength;

                for (int elementIndex = valuesStartOffset; elementIndex < valuesEndOffset; elementIndex++) {
                    valuesPositions.add(elementIndex);
                }
            }
        }
        Block newValues = getRawElementBlock().copyPositions(valuesPositions.elements(), 0, valuesPositions.size());
        return createArrayBlockInternal(0, length, newValueIsNull, newOffsets, newValues);
    }

    @Override
    public Block getRegion(int position, int length)
    {
        int positionCount = getPositionCount();
        checkValidRegion(positionCount, position, length);

        return createArrayBlockInternal(
                position + getOffsetBase(),
                length,
                getValueIsNull(),
                getOffsets(),
                getRawElementBlock());
    }

    @Override
    public long getRegionSizeInBytes(int position, int length)
    {
        int positionCount = getPositionCount();
        checkValidRegion(positionCount, position, length);

        int valueStart = getOffsets()[getOffsetBase() + position];
        int valueEnd = getOffsets()[getOffsetBase() + position + length];

        return getRawElementBlock().getRegionSizeInBytes(valueStart, valueEnd - valueStart) + ((Integer.BYTES + Byte.BYTES) * (long) length);
    }

    @Override
    public long getPositionsSizeInBytes(boolean[] positions)
    {
        checkValidPositions(positions, getPositionCount());
        boolean[] used = new boolean[getRawElementBlock().getPositionCount()];
        int usedPositionCount = 0;
        for (int i = 0; i < positions.length; ++i) {
            if (positions[i]) {
                usedPositionCount++;
                int valueStart = getOffsets()[getOffsetBase() + i];
                int valueEnd = getOffsets()[getOffsetBase() + i + 1];
                for (int j = valueStart; j < valueEnd; ++j) {
                    used[j] = true;
                }
            }
        }
        return getRawElementBlock().getPositionsSizeInBytes(used) + ((Integer.BYTES + Byte.BYTES) * (long) usedPositionCount);
    }

    @Override
    public Block copyRegion(int position, int length)
    {
        int positionCount = getPositionCount();
        checkValidRegion(positionCount, position, length);

        int startValueOffset = getOffset(position);
        int endValueOffset = getOffset(position + length);
        Block newValues = getRawElementBlock().copyRegion(startValueOffset, endValueOffset - startValueOffset);

        int[] newOffsets = compactOffsets(getOffsets(), position + getOffsetBase(), length);
        boolean[] valueIsNull = getValueIsNull();
        boolean[] newValueIsNull = valueIsNull == null ? null : compactArray(valueIsNull, position + getOffsetBase(), length);

        if (newValues == getRawElementBlock() && newOffsets == getOffsets() && newValueIsNull == valueIsNull) {
            return this;
        }
        return createArrayBlockInternal(0, length, newValueIsNull, newOffsets, newValues);
    }

    @Override
    public <T> T getObject(int position, Class<T> clazz)
    {
        if (clazz != Block.class) {
            throw new IllegalArgumentException("clazz must be Block.class");
        }
        checkReadablePosition(position);

        int startValueOffset = getOffset(position);
        int endValueOffset = getOffset(position + 1);
        return clazz.cast(getRawElementBlock().getRegion(startValueOffset, endValueOffset - startValueOffset));
    }

    @Override
    public void writePositionTo(int position, BlockBuilder blockBuilder)
    {
        checkReadablePosition(position);
        blockBuilder.appendStructureInternal(this, position);
    }

    @Override
    public Block getSingleValueBlock(int position)
    {
        checkReadablePosition(position);

        int startValueOffset = getOffset(position);
        int valueLength = getOffset(position + 1) - startValueOffset;
        Block newValues = getRawElementBlock().copyRegion(startValueOffset, valueLength);

        return createArrayBlockInternal(
                0,
                1,
                isNull(position) ? new boolean[] {true} : null,
                new int[] {0, valueLength},
                newValues);
    }

    @Override
    public long getEstimatedDataSizeForStats(int position)
    {
        checkReadablePosition(position);

        if (isNull(position)) {
            return 0;
        }

        int startValueOffset = getOffset(position);
        int endValueOffset = getOffset(position + 1);

        Block rawElementBlock = getRawElementBlock();
        long size = 0;
        for (int i = startValueOffset; i < endValueOffset; i++) {
            size += rawElementBlock.getEstimatedDataSizeForStats(i);
        }
        return size;
    }

    @Override
    public boolean isNull(int position)
    {
        checkReadablePosition(position);
        boolean[] valueIsNull = getValueIsNull();
        return valueIsNull != null && valueIsNull[position + getOffsetBase()];
    }

    public <T> T apply(ArrayBlockFunction<T> function, int position)
    {
        checkReadablePosition(position);

        int startValueOffset = getOffset(position);
        int endValueOffset = getOffset(position + 1);
        return function.apply(getRawElementBlock(), startValueOffset, endValueOffset - startValueOffset);
    }

    private void checkReadablePosition(int position)
    {
        if (position < 0 || position >= getPositionCount()) {
            throw new IllegalArgumentException("position is not valid");
        }
    }

    public interface ArrayBlockFunction<T>
    {
        T apply(Block block, int startPosition, int length);
    }
}
