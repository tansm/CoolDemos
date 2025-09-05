package org.example;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;

/**
 * 高性能紧凑位数组句柄，支持内联 long + 堆 long[] 存储。
 * 行为与 java.util.BitSet 一致，适用于 JDK 1.8。
 *
 * @param <T> 拥有位字段的类类型
 */
public final class CompactBitsHandle<T> {

    private final MethodHandle inlineGetter;
    private final MethodHandle inlineSetter;
    private final MethodHandle heapGetter;
    private final MethodHandle heapSetter;

    private static final int ADDRESS_BITS_PER_WORD = 6;
    private static final int BITS_PER_WORD = 1 << ADDRESS_BITS_PER_WORD; // 64

    public CompactBitsHandle(Class<T> owner, String inlineField, String heapField)
            throws NoSuchFieldException, IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        this.inlineGetter = lookup.unreflectGetter(owner.getDeclaredField(inlineField));
        this.inlineSetter = lookup.unreflectSetter(owner.getDeclaredField(inlineField));
        this.heapGetter = lookup.unreflectGetter(owner.getDeclaredField(heapField));
        this.heapSetter = lookup.unreflectSetter(owner.getDeclaredField(heapField));
    }

    // --- 字段访问（类型安全） ---

    private long getInline(T instance) {
        try {
            return (long) inlineGetter.invokeExact(instance);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get inlineBits", t);
        }
    }

    private void setInline(T instance, long value) {
        try {
            inlineSetter.invokeExact(instance, value);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set inlineBits", t);
        }
    }

    private long[] getHeap(T instance) {
        try {
            return (long[]) heapGetter.invokeExact(instance);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get heapArray", t);
        }
    }

    private void setHeap(T instance, long[] array) {
        try {
            heapSetter.invokeExact(instance, array);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set heapArray", t);
        }
    }

    // --- Word 级访问（统一 inline 与 heap） ---

    /**
     * 获取指定 word 索引的值。
     * wordIndex == 0 -> inlineBits
     * wordIndex >= 1 -> heapArray[wordIndex - 1]
     */
    private long getWord(T instance, int wordIndex) {
        if (wordIndex == 0) {
            return getInline(instance);
        } else {
            long[] heap = getHeap(instance);
            if (heap == null || wordIndex - 1 >= heap.length) {
                return 0L; // 读：越界视为 0
            }
            return heap[wordIndex - 1];
        }
    }

    /**
     * 设置指定 word 索引的值。
     */
    private void setWord(T instance, int wordIndex, long value) {
        if (wordIndex == 0) {
            setInline(instance, value);
        } else {
            long[] heap = getHeap(instance);
/*            if (heap == null || wordIndex - 1 >= heap.length) {
                throw new RuntimeException("Heap capacity exceeded: wordIndex=" + wordIndex);
            }*/
            heap[wordIndex - 1] = value;
        }
    }

    // --- 工具方法 ---

    private int wordIndex(int bitIndex) {
        return bitIndex >>> ADDRESS_BITS_PER_WORD;
    }

    private int bitOffset(int bitIndex) {
        return bitIndex & 63;
    }

    private void ensureHeapCapacity(T instance, int minWordIndex) {
        if (minWordIndex < 1) return;
        long[] heap = getHeap(instance);
        int requiredLen = minWordIndex;
        if (heap != null && heap.length >= requiredLen) {
            return;
        }
        int newCapacity = Math.max(requiredLen, heap == null ? 1 : heap.length << 1);
        long[] newHeap = new long[newCapacity];
        if (heap != null) {
            System.arraycopy(heap, 0, newHeap, 0, heap.length);
        }
        setHeap(instance, newHeap);
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
            throw new IllegalArgumentException(
                    "fromIndex: " + fromIndex + " > toIndex: " + toIndex);
        }
    }

    // --- 核心 API ---

    public void set(T instance, int bitIndex) {
        checkBitIndex(bitIndex);
        int wordIndex = wordIndex(bitIndex);
        int offset = bitOffset(bitIndex);
        long word = getWord(instance, wordIndex);
        word |= (1L << offset);
        setWord(instance, wordIndex, word);
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
        if (fromIndex == toIndex) return;

        int firstWordIndex = wordIndex(fromIndex);
        int lastWordIndex  = wordIndex(toIndex - 1);

        // 提前扩容（只一次）
        if (lastWordIndex > 0) {
            ensureHeapCapacity(instance, lastWordIndex + 1); // 确保 [0..lastWordIndex] 可写
        }

        long firstWordMask = -1L << bitOffset(fromIndex);
        long lastWordMask  = ~(-1L << bitOffset(toIndex));

        // First word
        long firstWord = getWord(instance, firstWordIndex);
        firstWord |= firstWordMask;
        setWord(instance, firstWordIndex, firstWord);

        if (firstWordIndex == lastWordIndex) {
            if (bitOffset(toIndex) != 0) {
                firstWord = getWord(instance, firstWordIndex);
                firstWord |= lastWordMask;
                setWord(instance, firstWordIndex, firstWord);
            }
            return;
        }

        // Middle words
        for (int i = firstWordIndex + 1; i < lastWordIndex; i++) {
            setWord(instance, i, -1L);
        }

        // Last word
        if (bitOffset(toIndex) != 0) {
            long lastWord = getWord(instance, lastWordIndex);
            lastWord |= lastWordMask;
            setWord(instance, lastWordIndex, lastWord);
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
        int offset = bitOffset(bitIndex);
        long word = getWord(instance, wordIndex);
        word &= ~(1L << offset);
        setWord(instance, wordIndex, word);
    }

    public void clear(T instance, int fromIndex, int toIndex) {
        checkRange(fromIndex, toIndex);
        if (fromIndex == toIndex) return;

        int firstWordIndex = wordIndex(fromIndex);
        int lastWordIndex  = wordIndex(toIndex - 1);

        long firstWordMask = -1L << bitOffset(fromIndex);
        long lastWordMask  = ~(-1L << bitOffset(toIndex));

        // First word
        long firstWord = getWord(instance, firstWordIndex);
        firstWord &= ~firstWordMask;
        setWord(instance, firstWordIndex, firstWord);

        if (firstWordIndex == lastWordIndex) {
            if (bitOffset(toIndex) != 0) {
                firstWord = getWord(instance, firstWordIndex);
                firstWord &= ~lastWordMask;
                setWord(instance, firstWordIndex, firstWord);
            }
            return;
        }

        // Middle words
        for (int i = firstWordIndex + 1; i < lastWordIndex; i++) {
            setWord(instance, i, 0L);
        }

        // Last word
        if (bitOffset(toIndex) != 0) {
            long lastWord = getWord(instance, lastWordIndex);
            lastWord &= ~lastWordMask;
            setWord(instance, lastWordIndex, lastWord);
        }
    }

    public void clear(T instance) {
        setInline(instance, 0L);
        long[] heap = getHeap(instance);
        if (heap != null) {
            Arrays.fill(heap, 0L);
        }
    }

    public boolean get(T instance, int bitIndex) {
        checkBitIndex(bitIndex);
        int wordIndex = wordIndex(bitIndex);
        int offset = bitOffset(bitIndex);
        long word = getWord(instance, wordIndex);
        return (word & (1L << offset)) != 0;
    }

    public void fill(T instance, long[] longs) {
        if (longs == null) {
            throw new NullPointerException("longs array is null");
        }
        setInline(instance, longs.length > 0 ? longs[0] : 0L);
        if (longs.length <= 1) {
            setHeap(instance, null);
        } else {
            long[] heap = new long[longs.length - 1];
            System.arraycopy(longs, 1, heap, 0, heap.length);
            setHeap(instance, heap);
        }
    }

    public long[] toLongArray(T instance) {
        long inline = getInline(instance);
        long[] heap = getHeap(instance);
        int heapLen = heap == null ? 0 : heap.length;
        long[] result = new long[1 + heapLen];
        result[0] = inline;
        if (heapLen > 0) {
            System.arraycopy(heap, 0, result, 1, heapLen);
        }
        return result;
    }

    public int nextSetBit(T instance, int fromIndex) {
        if (fromIndex < 0) {
            throw new IndexOutOfBoundsException("fromIndex < 0: " + fromIndex);
        }

        int wordIndex = wordIndex(fromIndex);
        int offset = bitOffset(fromIndex);

        long[] heap = getHeap(instance);
        int maxHeapWordIndex = heap == null ? 0 : heap.length + 1; // wordIndex: 1..heap.length

        while (true) {
            long word = getWord(instance, wordIndex);
            if (word != 0) {
                word >>>= offset;
                if (word != 0) {
                    return (wordIndex << 6) + offset + Long.numberOfTrailingZeros(word);
                }
            }

            // 使用实际数据边界退出
            if (wordIndex >= maxHeapWordIndex && wordIndex > 0) {
                break;
            }

            // 防止无限循环（bitIndex 溢出）
            if ((wordIndex << 6) >= Integer.MAX_VALUE - 64) {
                break;
            }

            wordIndex++;
            offset = 0;
        }
        return -1;
    }

    public int nextClearBit(T instance, int fromIndex) {
        if (fromIndex < 0) {
            throw new IndexOutOfBoundsException("fromIndex < 0: " + fromIndex);
        }

        int wordIndex = wordIndex(fromIndex);
        int offset = bitOffset(fromIndex);

        long[] heap = getHeap(instance);
        int maxHeapWordIndex = heap == null ? 0 : heap.length + 1;

        while (true) {
            long word = ~getWord(instance, wordIndex);
            if (word != 0) {
                word >>>= offset;
                if (word != 0) {
                    return (wordIndex << 6) + offset + Long.numberOfTrailingZeros(word);
                }
            }

            if (wordIndex >= maxHeapWordIndex && wordIndex > 0) {
                break;
            }

            if ((wordIndex << 6) >= Integer.MAX_VALUE - 64) {
                break;
            }

            wordIndex++;
            offset = 0;
        }
        return -1;
    }

    public int length(T instance) {
        long[] heap = getHeap(instance);
        if (heap == null || heap.length == 0) {
            return getInline(instance) == 0 ? 0 : 64 - Long.numberOfLeadingZeros(getInline(instance));
        }
        for (int i = heap.length - 1; i >= 0; i--) {
            if (heap[i] != 0) {
                return 64 + (i << 6) + 64 - Long.numberOfLeadingZeros(heap[i]);
            }
        }
        return getInline(instance) == 0 ? 0 : 64 - Long.numberOfLeadingZeros(getInline(instance));
    }

    public boolean isEmpty(T instance) {
        if (getInline(instance) != 0) return false;
        long[] heap = getHeap(instance);
        if (heap == null) return true;
        for (long w : heap) {
            if (w != 0) return false;
        }
        return true;
    }

    public int cardinality(T instance) {
        int n = Long.bitCount(getInline(instance));
        long[] heap = getHeap(instance);
        if (heap != null) {
            for (long w : heap) {
                n += Long.bitCount(w);
            }
        }
        return n;
    }

    public int size(T instance) {
        long[] heap = getHeap(instance);
        return 64 + (heap == null ? 0 : heap.length * 64);
    }
}