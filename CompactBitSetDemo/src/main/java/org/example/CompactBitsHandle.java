package org.example;

/**
 * 高性能紧凑位数组句柄，支持通过WordAccessor抽象存储，行为与java.util.BitSet一致。
 * 适用于JDK 1.8，专注于性能敏感场景的内存优化。
 *
 * @param <T> 拥有位字段的类类型
 */
public final class CompactBitsHandle<T> {

    private static final int ADDRESS_BITS_PER_WORD = 6;
    private static final long WORD_MASK = -1L;

    private final WordAccessor<T> accessor;

    public CompactBitsHandle(WordAccessor<T> accessor) {
        if (accessor == null) {
            throw new NullPointerException("WordAccessor cannot be null");
        }
        this.accessor = accessor;
    }

    // --- 工具方法 ---

    private int wordIndex(int bitIndex) {
        return bitIndex >> ADDRESS_BITS_PER_WORD;
    }

    private int bitOffset(int bitIndex) {
        return bitIndex & ((1 << ADDRESS_BITS_PER_WORD) - 1);
    }

    private void checkBitIndex(int bitIndex) {
        if (bitIndex < 0) {
            throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);
        }
    }

    private void checkRange(int fromIndex, int toIndex) {
        if (fromIndex < 0) {
            throw new IndexOutOfBoundsException("fromIndex < 0: " + fromIndex);
        }
        if (toIndex < 0) {
            throw new IndexOutOfBoundsException("toIndex < 0: " + toIndex);
        }
        if (fromIndex > toIndex) {
            throw new IndexOutOfBoundsException("fromIndex: " + fromIndex + " > toIndex: " + toIndex);
        }
    }

    // --- 核心 API ---

    public void set(T instance, int bitIndex) {
        checkBitIndex(bitIndex);
        int wordIndex = wordIndex(bitIndex);
        accessor.ensureCapacity(instance, wordIndex + 1);
        long word = accessor.getWord(instance, wordIndex);
        word |= (1L << bitOffset(bitIndex));
        accessor.setWord(instance, wordIndex, word);
    }

    public void set(T instance, int bitIndex, boolean value) {
        if (value) {
            set(instance, bitIndex);
        } else {
            clear(instance, bitIndex);
        }
    }

    public void set(T instance, int fromIndex, int toIndex) {
        checkRange(fromIndex, toIndex);
        if (fromIndex == toIndex) {
            return;
        }
        int firstWordIndex = wordIndex(fromIndex);
        int lastWordIndex = wordIndex(toIndex - 1);
        accessor.ensureCapacity(instance, lastWordIndex + 1);
        long firstWordMask = WORD_MASK << bitOffset(fromIndex);
        long lastWordMask = WORD_MASK >>> -bitOffset(toIndex);
        if (firstWordIndex == lastWordIndex) {
            long word = accessor.getWord(instance, firstWordIndex);
            word |= (firstWordMask & lastWordMask);
            accessor.setWord(instance, firstWordIndex, word);
        } else {
            long word = accessor.getWord(instance, firstWordIndex);
            word |= firstWordMask;
            accessor.setWord(instance, firstWordIndex, word);
            for (int i = firstWordIndex + 1; i < lastWordIndex; i++) {
                accessor.setWord(instance, i, WORD_MASK);
            }
            word = accessor.getWord(instance, lastWordIndex);
            word |= lastWordMask;
            accessor.setWord(instance, lastWordIndex, word);
        }
    }

    public void set(T instance, int fromIndex, int toIndex, boolean value) {
        if (value) {
            set(instance, fromIndex, toIndex);
        } else {
            clear(instance, fromIndex, toIndex);
        }
    }

    public void clear(T instance, int bitIndex) {
        checkBitIndex(bitIndex);
        int wordIndex = wordIndex(bitIndex);
        if (wordIndex >= accessor.getWordCount(instance)) {
            return;
        }
        long word = accessor.getWord(instance, wordIndex);
        word &= ~(1L << bitOffset(bitIndex));
        accessor.setWord(instance, wordIndex, word);
        accessor.trim(instance);
    }

    public void clear(T instance, int fromIndex, int toIndex) {
        checkRange(fromIndex, toIndex);
        if (fromIndex == toIndex) {
            return;
        }
        int firstWordIndex = wordIndex(fromIndex);
        int count = accessor.getWordCount(instance);
        if (firstWordIndex >= count) {
            return;
        }
        int lastWordIndex = wordIndex(toIndex - 1);
        if (lastWordIndex >= count) {
            lastWordIndex = count - 1;
            toIndex = count << ADDRESS_BITS_PER_WORD;
        }
        long firstWordMask = WORD_MASK << bitOffset(fromIndex);
        long lastWordMask = WORD_MASK >>> -bitOffset(toIndex);
        if (firstWordIndex == lastWordIndex) {
            long word = accessor.getWord(instance, firstWordIndex);
            word &= ~(firstWordMask & lastWordMask);
            accessor.setWord(instance, firstWordIndex, word);
        } else {
            long word = accessor.getWord(instance, firstWordIndex);
            word &= ~firstWordMask;
            accessor.setWord(instance, firstWordIndex, word);
            for (int i = firstWordIndex + 1; i < lastWordIndex; i++) {
                accessor.setWord(instance, i, 0L);
            }
            word = accessor.getWord(instance, lastWordIndex);
            word &= ~lastWordMask;
            accessor.setWord(instance, lastWordIndex, word);
        }
        accessor.trim(instance);
    }

    public void clear(T instance) {
        int count = accessor.getWordCount(instance);
        for (int i = 0; i < count; i++) {
            accessor.setWord(instance, i, 0L);
        }
        accessor.trim(instance);
    }

    public boolean get(T instance, int bitIndex) {
        checkBitIndex(bitIndex);
        int wordIndex = wordIndex(bitIndex);
        long word = accessor.getWord(instance, wordIndex);
        return (word & (1L << bitOffset(bitIndex))) != 0;
    }

    public void fill(T instance, long[] longs) {
        if (longs == null) {
            throw new NullPointerException("longs array is null");
        }
        int n = longs.length;
        while (n > 0 && longs[n - 1] == 0L) {
            n--;
        }
        accessor.ensureCapacity(instance, n);
        for (int i = 0; i < n; i++) {
            accessor.setWord(instance, i, longs[i]);
        }
        int currentCount = accessor.getWordCount(instance);
        for (int i = n; i < currentCount; i++) {
            accessor.setWord(instance, i, 0L);
        }
        accessor.trim(instance);
    }

    public long[] toLongArray(T instance) {
        int n = accessor.getWordCount(instance);
        long[] result = new long[n];
        for (int i = 0; i < n; i++) {
            result[i] = accessor.getWord(instance, i);
        }
        return result;
    }

    public int nextSetBit(T instance, int fromIndex) {
        if (fromIndex < 0) {
            throw new IndexOutOfBoundsException("fromIndex < 0: " + fromIndex);
        }
        int wordIndex = wordIndex(fromIndex);
        int maxWordIndex = accessor.getWordCount(instance);
        if (wordIndex >= maxWordIndex) {
            return -1;
        }
        int offset = bitOffset(fromIndex);
        while (wordIndex < maxWordIndex) {
            long word = accessor.getWord(instance, wordIndex);
            if (offset > 0) {
                word >>>= offset;
                if (word != 0) {
                    return (wordIndex << ADDRESS_BITS_PER_WORD) + offset + Long.numberOfTrailingZeros(word);
                }
                offset = 0;
                wordIndex++;
            } else {
                if (word != 0) {
                    return (wordIndex << ADDRESS_BITS_PER_WORD) + Long.numberOfTrailingZeros(word);
                }
                wordIndex++;
            }
        }
        return -1;
    }

    public int nextClearBit(T instance, int fromIndex) {
        if (fromIndex < 0) {
            throw new IndexOutOfBoundsException("fromIndex < 0: " + fromIndex);
        }
        int wordIndex = wordIndex(fromIndex);
        int maxWordIndex = accessor.getWordCount(instance);
        int offset = bitOffset(fromIndex);
        while (wordIndex < maxWordIndex) {
            long word = ~accessor.getWord(instance, wordIndex);
            if (offset > 0) {
                word >>>= offset;
                if (word != 0) {
                    return (wordIndex << ADDRESS_BITS_PER_WORD) + offset + Long.numberOfTrailingZeros(word);
                }
                offset = 0;
                wordIndex++;
            } else {
                if (word != 0) {
                    return (wordIndex << ADDRESS_BITS_PER_WORD) + Long.numberOfTrailingZeros(word);
                }
                wordIndex++;
            }
        }
        return wordIndex << ADDRESS_BITS_PER_WORD;
    }

    public int length(T instance) {
        int count = accessor.getWordCount(instance);
        for (int i = count - 1; i >= 0; i--) {
            long word = accessor.getWord(instance, i);
            if (word != 0) {
                return (i << ADDRESS_BITS_PER_WORD) + 64 - Long.numberOfLeadingZeros(word);
            }
        }
        return 0;
    }

    public boolean isEmpty(T instance) {
        int count = accessor.getWordCount(instance);
        for (int i = 0; i < count; i++) {
            if (accessor.getWord(instance, i) != 0) {
                return false;
            }
        }
        return true;
    }

    public int cardinality(T instance) {
        int sum = 0;
        int count = accessor.getWordCount(instance);
        for (int i = 0; i < count; i++) {
            sum += Long.bitCount(accessor.getWord(instance, i));
        }
        return sum;
    }

    public int size(T instance) {
        return accessor.getWordCount(instance) << ADDRESS_BITS_PER_WORD;
    }
}