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

import io.airlift.slice.Slice;
import io.airlift.slice.Slices;
import org.openjdk.jol.info.ClassLayout;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import static io.airlift.slice.SizeOf.sizeOf;
import static io.trino.spi.block.BlockUtil.*;
import static io.trino.spi.block.DictionaryId.randomDictionaryId;
import static java.lang.Math.min;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

public class DictionaryBlock
        implements Block
{
    private static final int INSTANCE_SIZE = ClassLayout.parseClass(DictionaryBlock.class).instanceSize() + ClassLayout.parseClass(DictionaryId.class).instanceSize();

    private final int positionCount;
    private final Block dictionary;
    private final int idsOffset;
    private final int[] ids;
    private final long retainedSizeInBytes;
    private volatile long sizeInBytes = -1;
    private volatile long logicalSizeInBytes = -1;
    private volatile int uniqueIds = -1;
    // isSequentialIds is only valid when uniqueIds is computed
    private volatile boolean isSequentialIds;
    private final DictionaryId dictionarySourceId;
    private final boolean mayHaveNull;

    public DictionaryBlock(Block dictionary, int[] ids)
    {
        this(requireNonNull(ids, "ids is null").length, dictionary, ids);
    }

    public DictionaryBlock(int positionCount, Block dictionary, int[] ids)
    {
        this(0, positionCount, dictionary, ids, false, randomDictionaryId());
    }

    public DictionaryBlock(int positionCount, Block dictionary, int[] ids, DictionaryId dictionaryId)
    {
        this(0, positionCount, dictionary, ids, false, dictionaryId);
    }

    public DictionaryBlock(int positionCount, Block dictionary, int[] ids, boolean dictionaryIsCompacted)
    {
        this(0, positionCount, dictionary, ids, dictionaryIsCompacted, randomDictionaryId());
    }

    public DictionaryBlock(int positionCount, Block dictionary, int[] ids, boolean dictionaryIsCompacted, DictionaryId dictionarySourceId)
    {
        this(0, positionCount, dictionary, ids, dictionaryIsCompacted, dictionarySourceId);
    }

    public DictionaryBlock(int idsOffset, int positionCount, Block dictionary, int[] ids, boolean dictionaryIsCompacted, DictionaryId dictionarySourceId)
    {
        this(idsOffset, positionCount, dictionary, ids, dictionaryIsCompacted, false, dictionarySourceId);
    }

    public DictionaryBlock(int idsOffset, int positionCount, Block dictionary, int[] ids, boolean dictionaryIsCompacted, boolean isSequentialIds, DictionaryId dictionarySourceId)
    {
        requireNonNull(dictionary, "dictionary is null");
        requireNonNull(ids, "ids is null");

        if (positionCount < 0) {
            throw new IllegalArgumentException("positionCount is negative");
        }

        this.idsOffset = idsOffset;
        if (ids.length - idsOffset < positionCount) {
            throw new IllegalArgumentException("ids length is less than positionCount");
        }

        this.positionCount = positionCount;
        this.dictionary = dictionary;
        this.ids = ids;
        this.dictionarySourceId = requireNonNull(dictionarySourceId, "dictionarySourceId is null");
        this.retainedSizeInBytes = INSTANCE_SIZE + sizeOf(ids);
        // avoid eager loading of lazy dictionaries
        this.mayHaveNull = positionCount > 0 && (!dictionary.isLoaded() || dictionary.mayHaveNull());

        if (dictionaryIsCompacted) {
            if (dictionary instanceof DictionaryBlock) {
                throw new IllegalArgumentException("compacted dictionary should not have dictionary base block");
            }
            this.sizeInBytes = dictionary.getSizeInBytes() + (Integer.BYTES * (long) positionCount);
            this.uniqueIds = dictionary.getPositionCount();
        }

        if (isSequentialIds && !dictionaryIsCompacted) {
            throw new IllegalArgumentException("sequential ids flag is only valid for compacted dictionary");
        }
        this.isSequentialIds = isSequentialIds;
    }

    int[] getRawIds()
    {
        return ids;
    }

    int getRawIdsOffset()
    {
        return idsOffset;
    }

    @Override
    public int getSliceLength(int position)
    {
        return dictionary.getSliceLength(getId(position));
    }

    @Override
    public byte getByte(int position, int offset)
    {
        return dictionary.getByte(getId(position), offset);
    }

    @Override
    public short getShort(int position, int offset)
    {
        return dictionary.getShort(getId(position), offset);
    }

    @Override
    public int getInt(int position, int offset)
    {
        return dictionary.getInt(getId(position), offset);
    }

    @Override
    public long getLong(int position, int offset)
    {
        return dictionary.getLong(getId(position), offset);
    }

    @Override
    public Slice getSlice(int position, int offset, int length)
    {
        return dictionary.getSlice(getId(position), offset, length);
    }

    @Override
    public <T> T getObject(int position, Class<T> clazz)
    {
        return dictionary.getObject(getId(position), clazz);
    }

    @Override
    public boolean bytesEqual(int position, int offset, Slice otherSlice, int otherOffset, int length)
    {
        return dictionary.bytesEqual(getId(position), offset, otherSlice, otherOffset, length);
    }

    @Override
    public int bytesCompare(int position, int offset, int length, Slice otherSlice, int otherOffset, int otherLength)
    {
        return dictionary.bytesCompare(getId(position), offset, length, otherSlice, otherOffset, otherLength);
    }

    @Override
    public void writeBytesTo(int position, int offset, int length, BlockBuilder blockBuilder)
    {
        dictionary.writeBytesTo(getId(position), offset, length, blockBuilder);
    }

    @Override
    public void writePositionTo(int position, BlockBuilder blockBuilder)
    {
        dictionary.writePositionTo(getId(position), blockBuilder);
    }

    @Override
    public boolean equals(int position, int offset, Block otherBlock, int otherPosition, int otherOffset, int length)
    {
        return dictionary.equals(getId(position), offset, otherBlock, otherPosition, otherOffset, length);
    }

    @Override
    public long hash(int position, int offset, int length)
    {
        return dictionary.hash(getId(position), offset, length);
    }

    @Override
    public int compareTo(int leftPosition, int leftOffset, int leftLength, Block rightBlock, int rightPosition, int rightOffset, int rightLength)
    {
        return dictionary.compareTo(getId(leftPosition), leftOffset, leftLength, rightBlock, rightPosition, rightOffset, rightLength);
    }

    @Override
    public Block getSingleValueBlock(int position)
    {
        return dictionary.getSingleValueBlock(getId(position));
    }

    @Override
    public int getPositionCount()
    {
        return positionCount;
    }

    @Override
    public long getSizeInBytes()
    {
        if (sizeInBytes == -1) {
            calculateCompactSize();
        }
        return sizeInBytes;
    }

    private void calculateCompactSize()
    {
        int uniqueIds = 0;
        boolean[] used = new boolean[dictionary.getPositionCount()];
        int previousPosition = -1;
        boolean isSequentialIds = true;
        for (int i = 0; i < positionCount; i++) {
            int position = getId(i);
            // Avoid branching
            uniqueIds += used[position] ? 0 : 1;
            used[position] = true;

            isSequentialIds = isSequentialIds && previousPosition < position;
            previousPosition = position;
        }

        long dictionaryBlockSize;

        if (dictionary instanceof DictionaryBlock) {
            // dictionary is nested, compaction would unnest it and nested ids
            // array shouldn't be accounted for
            DictionaryBlock nestedDictionary = (DictionaryBlock) dictionary;
            if (uniqueIds == dictionary.getPositionCount()) {
                // dictionary is compact, all positions were used
                dictionaryBlockSize = nestedDictionary.getCompactedDictionarySizeInBytes();
            }
            else {
                dictionaryBlockSize = nestedDictionary.getCompactedDictionaryPositionsSizeInBytes(used);
            }
            // nested dictionaries are assumed not to have sequential ids
            isSequentialIds = false;
        }
        else {
            if (uniqueIds == dictionary.getPositionCount()) {
                // dictionary is compact, all positions were used
                dictionaryBlockSize = dictionary.getSizeInBytes();
            }
            else {
                dictionaryBlockSize = dictionary.getPositionsSizeInBytes(used, uniqueIds);
            }
        }

        this.sizeInBytes = dictionaryBlockSize + (Integer.BYTES * (long) positionCount);
        this.uniqueIds = uniqueIds;
        this.isSequentialIds = isSequentialIds;
    }

    /**
     * Returns size of compacted dictionary. This is computed as if the dictionaries were unnested.
     */
    private long getCompactedDictionarySizeInBytes()
    {
        if (sizeInBytes == -1) {
            calculateCompactSize();
        }

        return sizeInBytes - (Integer.BYTES * (long) positionCount);
    }

    /**
     * Returns size of compacted dictionary for given positions. This is computed as if the dictionaries were unnested.
     */
    private long getCompactedDictionaryPositionsSizeInBytes(boolean[] positions)
    {
        boolean[] used = new boolean[dictionary.getPositionCount()];
        for (int i = 0; i < positions.length; i++) {
            if (positions[i]) {
                used[getId(i)] = true;
            }
        }

        if (dictionary instanceof DictionaryBlock) {
            return ((DictionaryBlock) dictionary).getCompactedDictionaryPositionsSizeInBytes(used);
        }

        return dictionary.getPositionsSizeInBytes(used);
    }

    @Override
    public long getLogicalSizeInBytes()
    {
        if (logicalSizeInBytes >= 0) {
            return logicalSizeInBytes;
        }

        // Calculation of logical size can be performed as part of calculateCompactSize() with minor modifications.
        // Keeping this calculation separate as this is a little more expensive and may not be called as often.
        long sizeInBytes = 0;
        long[] seenSizes = new long[dictionary.getPositionCount()];
        Arrays.fill(seenSizes, -1L);
        for (int i = 0; i < getPositionCount(); i++) {
            int position = getId(i);
            if (seenSizes[position] < 0) {
                seenSizes[position] = dictionary.getRegionSizeInBytes(position, 1);
            }
            sizeInBytes += seenSizes[position];
        }

        logicalSizeInBytes = sizeInBytes;
        return sizeInBytes;
    }

    @Override
    public long getRegionSizeInBytes(int positionOffset, int length)
    {
        if (positionOffset == 0 && length == getPositionCount()) {
            // Calculation of getRegionSizeInBytes is expensive in this class.
            // On the other hand, getSizeInBytes result is cached.
            return getSizeInBytes();
        }

        boolean[] used = new boolean[dictionary.getPositionCount()];
        for (int i = positionOffset; i < positionOffset + length; i++) {
            used[getId(i)] = true;
        }
        return dictionary.getPositionsSizeInBytes(used) + Integer.BYTES * (long) length;
    }

    @Override
    public long getPositionsSizeInBytes(boolean[] positions)
    {
        checkValidPositions(positions, positionCount);

        boolean[] used = new boolean[dictionary.getPositionCount()];
        for (int i = 0; i < positions.length; i++) {
            if (positions[i]) {
                used[getId(i)] = true;
            }
        }
        return dictionary.getPositionsSizeInBytes(used) + (Integer.BYTES * (long) countUsedPositions(positions));
    }

    @Override
    public long getRetainedSizeInBytes()
    {
        return retainedSizeInBytes + dictionary.getRetainedSizeInBytes();
    }

    @Override
    public long getEstimatedDataSizeForStats(int position)
    {
        return dictionary.getEstimatedDataSizeForStats(getId(position));
    }

    @Override
    public void retainedBytesForEachPart(BiConsumer<Object, Long> consumer)
    {
        consumer.accept(dictionary, dictionary.getRetainedSizeInBytes());
        consumer.accept(ids, sizeOf(ids));
        consumer.accept(this, (long) INSTANCE_SIZE);
    }

    @Override
    public String getEncodingName()
    {
        return DictionaryBlockEncoding.NAME;
    }

    @Override
    public Block copyPositions(int[] positions, int offset, int length)
    {
        checkArrayRange(positions, offset, length);

        if (length <= 1 || dictionary instanceof DictionaryBlock || uniqueIds == positionCount) {
            // each block position is unique or the dictionary is a nested dictionary block,
            // therefore it makes sense to unwrap this outer dictionary layer directly
            int[] positionsToCopy = new int[length];
            for (int i = 0; i < length; i++) {
                positionsToCopy[i] = getId(positions[offset + i]);
            }
            return dictionary.copyPositions(positionsToCopy, 0, length);
        }

        IntArrayList positionsToCopy = new IntArrayList();
        Int2IntOpenHashMap oldIndexToNewIndex = new Int2IntOpenHashMap(min(length, dictionary.getPositionCount()));
        int[] newIds = new int[length];

        for (int i = 0; i < length; i++) {
            int position = positions[offset + i];
            int oldIndex = getId(position);
            int newId = oldIndexToNewIndex.putIfAbsent(oldIndex, positionsToCopy.size());
            if (newId == -1) {
                newId = positionsToCopy.size();
                positionsToCopy.add(oldIndex);
            }
            newIds[i] = newId;
        }
        Block compactDictionary = dictionary.copyPositions(positionsToCopy.elements(), 0, positionsToCopy.size());
        if (positionsToCopy.size() == length) {
            // discovered that all positions are unique, so return the unwrapped underlying dictionary directly
            return compactDictionary;
        }
        return new DictionaryBlock(
                0,
                length,
                compactDictionary,
                newIds,
                true, // new dictionary is compact
                false,
                randomDictionaryId());
    }

    @Override
    public Block getRegion(int positionOffset, int length)
    {
        checkValidRegion(positionCount, positionOffset, length);

        if (length == positionCount) {
            return this;
        }

        return new DictionaryBlock(idsOffset + positionOffset, length, dictionary, ids, false, dictionarySourceId);
    }

    @Override
    public Block copyRegion(int position, int length)
    {
        checkValidRegion(positionCount, position, length);
        // Avoid repeated volatile reads to the uniqueIds field
        int uniqueIds = this.uniqueIds;
        if (length <= 1 || (uniqueIds == dictionary.getPositionCount() && isSequentialIds)) {
            // copy the contiguous range directly via copyRegion
            return dictionary.copyRegion(getId(position), length);
        }
        if (dictionary instanceof DictionaryBlock || uniqueIds == positionCount) {
            // each block position is unique or the dictionary is a nested dictionary block,
            // therefore it makes sense to unwrap this outer dictionary layer directly
            return dictionary.copyPositions(ids, idsOffset + position, length);
        }
        int[] newIds = Arrays.copyOfRange(ids, idsOffset + position, idsOffset + position + length);
        DictionaryBlock dictionaryBlock = new DictionaryBlock(dictionary, newIds);
        return dictionaryBlock.compact();
    }

    @Override
    public boolean mayHaveNull()
    {
        return positionCount > 0 && dictionary.mayHaveNull();
    }

    @Override
    public boolean isNull(int position)
    {
        checkValidPosition(position, positionCount);
        return mayHaveNull && dictionary.isNull(getIdUnchecked(position));
    }

    @Override
    public Block getPositions(int[] positions, int offset, int length)
    {
        checkArrayRange(positions, offset, length);

        int[] newIds = new int[length];
        boolean isCompact = length >= dictionary.getPositionCount() && isCompact();
        boolean[] seen = null;
        if (isCompact) {
            seen = new boolean[dictionary.getPositionCount()];
        }
        for (int i = 0; i < length; i++) {
            newIds[i] = getId(positions[offset + i]);
            if (isCompact) {
                seen[newIds[i]] = true;
            }
        }
        for (int i = 0; i < dictionary.getPositionCount() && isCompact; i++) {
            isCompact &= seen[i];
        }
        return new DictionaryBlock(newIds.length, getDictionary(), newIds, isCompact, getDictionarySourceId());
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("DictionaryBlock{");
        sb.append("positionCount=").append(getPositionCount());
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean isLoaded()
    {
        return dictionary.isLoaded();
    }

    @Override
    public Block getLoadedBlock()
    {
        Block loadedDictionary = dictionary.getLoadedBlock();

        if (loadedDictionary == dictionary) {
            return this;
        }
        return new DictionaryBlock(idsOffset, getPositionCount(), loadedDictionary, ids, false, randomDictionaryId());
    }

    @Override
    public final List<Block> getChildren()
    {
        return singletonList(getDictionary());
    }

    public Block getDictionary()
    {
        return dictionary;
    }

    Slice getIds()
    {
        return Slices.wrappedIntArray(ids, idsOffset, positionCount);
    }

    boolean isSequentialIds()
    {
        if (uniqueIds == -1) {
            calculateCompactSize();
        }

        return isSequentialIds;
    }

    int getUniqueIds()
    {
        if (uniqueIds == -1) {
            calculateCompactSize();
        }

        return uniqueIds;
    }

    public int getId(int position)
    {
        checkValidPosition(position, positionCount);
        return getIdUnchecked(position);
    }

    private int getIdUnchecked(int position)
    {
        return ids[position + idsOffset];
    }

    public DictionaryId getDictionarySourceId()
    {
        return dictionarySourceId;
    }

    public boolean isCompact()
    {
        if (dictionary instanceof DictionaryBlock) {
            return false;
        }

        if (uniqueIds == -1) {
            calculateCompactSize();
        }
        return uniqueIds == dictionary.getPositionCount();
    }

    public DictionaryBlock compact()
    {
        if (isCompact()) {
            return this;
        }

        DictionaryBlock unnested = unnest();
        if (unnested != this) {
            return unnested.compact();
        }

        // determine which dictionary entries are referenced and build a reindex for them
        int dictionarySize = dictionary.getPositionCount();
        IntArrayList dictionaryPositionsToCopy = new IntArrayList(min(dictionarySize, positionCount));
        int[] remapIndex = new int[dictionarySize];
        Arrays.fill(remapIndex, -1);

        int newIndex = 0;
        for (int i = 0; i < positionCount; i++) {
            int dictionaryIndex = getId(i);
            if (remapIndex[dictionaryIndex] == -1) {
                dictionaryPositionsToCopy.add(dictionaryIndex);
                remapIndex[dictionaryIndex] = newIndex;
                newIndex++;
            }
        }

        // entire dictionary is referenced
        if (dictionaryPositionsToCopy.size() == dictionarySize) {
            return this;
        }

        // compact the dictionary
        int[] newIds = new int[positionCount];
        for (int i = 0; i < positionCount; i++) {
            int newId = remapIndex[getId(i)];
            if (newId == -1) {
                throw new IllegalStateException("reference to a non-existent key");
            }
            newIds[i] = newId;
        }
        try {
            Block compactDictionary = dictionary.copyPositions(dictionaryPositionsToCopy.elements(), 0, dictionaryPositionsToCopy.size());
            return new DictionaryBlock(
                    0,
                    positionCount,
                    compactDictionary,
                    newIds,
                    true,
                    // Copied dictionary positions match ids sequence. Therefore new
                    // compact dictionary block has sequential ids only if single position
                    // is not used more than once.
                    uniqueIds == positionCount,
                    randomDictionaryId());
        }
        catch (UnsupportedOperationException e) {
            // ignore if copy positions is not supported for the dictionary block
            return this;
        }
    }

    private DictionaryBlock unnest()
    {
        if (!(dictionary instanceof DictionaryBlock)) {
            return this;
        }

        int[] ids = new int[positionCount];
        for (int i = 0; i < positionCount; i++) {
            ids[i] = getId(i);
        }

        Block dictionary = this.dictionary;
        while (dictionary instanceof DictionaryBlock) {
            DictionaryBlock nestedDictionary = (DictionaryBlock) dictionary;
            for (int i = 0; i < positionCount; i++) {
                ids[i] = nestedDictionary.getId(ids[i]);
            }
            dictionary = nestedDictionary.getDictionary();
        }

        return new DictionaryBlock(dictionary, ids);
    }
}
