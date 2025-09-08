package org.example;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class CompactBitsHandleBenchmark {

    private static final int MAX_BIT_INDEX = 1_000_000; // 测试范围：最多设置 100 万位
    private static final int OPERATIONS = 10_000; // 操作次数

    @State(Scope.Thread)
    public static class BitSetState {
        public BitSet bitSet = new BitSet();
        public TestBean compactBean = new TestBean();
        public Random random = new Random(42); // 固定种子以确保可重复性
        public int[] randomIndices;

        @Setup(Level.Trial)
        public void setup() {
            randomIndices = new int[OPERATIONS];
            for (int i = 0; i < OPERATIONS; i++) {
                randomIndices[i] = random.nextInt(MAX_BIT_INDEX);
            }
            // 预填充一些数据
            for (int i = 0; i < MAX_BIT_INDEX / 2; i += 2) {
                bitSet.set(i);
                compactBean.set(i);
            }
        }
    }

    // --- 单比特 set ---

    @Benchmark
    public void benchmarkBitSet_Set(BitSetState state, Blackhole blackhole) {
        for (int index : state.randomIndices) {
            state.bitSet.set(index);
        }
        blackhole.consume(state.bitSet);
    }

    @Benchmark
    public void benchmarkCompact_Set(BitSetState state, Blackhole blackhole) {
        for (int index : state.randomIndices) {
            state.compactBean.set(index);
        }
        blackhole.consume(state.compactBean);
    }

    // --- 单比特 get ---

    @Benchmark
    public void benchmarkBitSet_Get(BitSetState state, Blackhole blackhole) {
        for (int index : state.randomIndices) {
            blackhole.consume(state.bitSet.get(index));
        }
    }

    @Benchmark
    public void benchmarkCompact_Get(BitSetState state, Blackhole blackhole) {
        for (int index : state.randomIndices) {
            blackhole.consume(state.compactBean.get(index));
        }
    }

    // --- 单比特 clear ---

    @Benchmark
    public void benchmarkBitSet_Clear(BitSetState state, Blackhole blackhole) {
        for (int index : state.randomIndices) {
            state.bitSet.clear(index);
        }
        blackhole.consume(state.bitSet);
    }

    @Benchmark
    public void benchmarkCompact_Clear(BitSetState state, Blackhole blackhole) {
        for (int index : state.randomIndices) {
            state.compactBean.clear(index);
        }
        blackhole.consume(state.compactBean);
    }

    // --- 范围 set ---

    @Benchmark
    public void benchmarkBitSet_SetRange(BitSetState state, Blackhole blackhole) {
        for (int i = 0; i < OPERATIONS; i++) {
            int from = state.random.nextInt(MAX_BIT_INDEX / 2);
            int to = from + state.random.nextInt(1000) + 1; // 范围 1-1000
            state.bitSet.set(from, to);
        }
        blackhole.consume(state.bitSet);
    }

    @Benchmark
    public void benchmarkCompact_SetRange(BitSetState state, Blackhole blackhole) {
        for (int i = 0; i < OPERATIONS; i++) {
            int from = state.random.nextInt(MAX_BIT_INDEX / 2);
            int to = from + state.random.nextInt(1000) + 1;
            state.compactBean.set(from, to);
        }
        blackhole.consume(state.compactBean);
    }

    // --- 范围 clear ---

    @Benchmark
    public void benchmarkBitSet_ClearRange(BitSetState state, Blackhole blackhole) {
        for (int i = 0; i < OPERATIONS; i++) {
            int from = state.random.nextInt(MAX_BIT_INDEX / 2);
            int to = from + state.random.nextInt(1000) + 1;
            state.bitSet.clear(from, to);
        }
        blackhole.consume(state.bitSet);
    }

    @Benchmark
    public void benchmarkCompact_ClearRange(BitSetState state, Blackhole blackhole) {
        for (int i = 0; i < OPERATIONS; i++) {
            int from = state.random.nextInt(MAX_BIT_INDEX / 2);
            int to = from + state.random.nextInt(1000) + 1;
            state.compactBean.clear(from, to);
        }
        blackhole.consume(state.compactBean);
    }

    // --- nextSetBit ---

    @Benchmark
    public void benchmarkBitSet_NextSetBit(BitSetState state, Blackhole blackhole) {
        int from = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            from = state.bitSet.nextSetBit(from + 1);
            blackhole.consume(from);
        }
    }

    @Benchmark
    public void benchmarkCompact_NextSetBit(BitSetState state, Blackhole blackhole) {
        int from = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            from = state.compactBean.nextSetBit(from + 1);
            blackhole.consume(from);
        }
    }

    // --- nextClearBit ---

    @Benchmark
    public void benchmarkBitSet_NextClearBit(BitSetState state, Blackhole blackhole) {
        int from = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            from = state.bitSet.nextClearBit(from + 1);
            blackhole.consume(from);
        }
    }

    @Benchmark
    public void benchmarkCompact_NextClearBit(BitSetState state, Blackhole blackhole) {
        int from = 0;
        for (int i = 0; i < OPERATIONS; i++) {
            from = state.compactBean.nextClearBit(from + 1);
            blackhole.consume(from);
        }
    }

    // --- cardinality ---

    @Benchmark
    public void benchmarkBitSet_Cardinality(BitSetState state, Blackhole blackhole) {
        blackhole.consume(state.bitSet.cardinality());
    }

    @Benchmark
    public void benchmarkCompact_Cardinality(BitSetState state, Blackhole blackhole) {
        blackhole.consume(state.compactBean.cardinality());
    }

    // --- length ---

    @Benchmark
    public void benchmarkBitSet_Length(BitSetState state, Blackhole blackhole) {
        blackhole.consume(state.bitSet.length());
    }

    @Benchmark
    public void benchmarkCompact_Length(BitSetState state, Blackhole blackhole) {
        blackhole.consume(state.compactBean.length());
    }

    // --- isEmpty ---

    @Benchmark
    public void benchmarkBitSet_IsEmpty(BitSetState state, Blackhole blackhole) {
        blackhole.consume(state.bitSet.isEmpty());
    }

    @Benchmark
    public void benchmarkCompact_IsEmpty(BitSetState state, Blackhole blackhole) {
        blackhole.consume(state.compactBean.isEmpty());
    }

    // --- size ---

    @Benchmark
    public void benchmarkBitSet_Size(BitSetState state, Blackhole blackhole) {
        blackhole.consume(state.bitSet.size());
    }

    @Benchmark
    public void benchmarkCompact_Size(BitSetState state, Blackhole blackhole) {
        blackhole.consume(state.compactBean.size());
    }

    // --- toLongArray ---

    @Benchmark
    public void benchmarkBitSet_ToLongArray(BitSetState state, Blackhole blackhole) {
        blackhole.consume(state.bitSet.toLongArray());
    }

    @Benchmark
    public void benchmarkCompact_ToLongArray(BitSetState state, Blackhole blackhole) {
        blackhole.consume(state.compactBean.toLongArray());
    }

    // --- fill (使用 BitSet.valueOf) ---

    @Benchmark
    public void benchmarkBitSet_Fill(BitSetState state, Blackhole blackhole) {
        long[] data = new long[1000];
        for (int i = 0; i < data.length; i++) {
            data[i] = state.random.nextLong();
        }
        state.bitSet = BitSet.valueOf(data);
        blackhole.consume(state.bitSet);
    }

    @Benchmark
    public void benchmarkCompact_Fill(BitSetState state, Blackhole blackhole) {
        long[] data = new long[1000];
        for (int i = 0; i < data.length; i++) {
            data[i] = state.random.nextLong();
        }
        state.compactBean.fill(data);
        blackhole.consume(state.compactBean);
    }

    // --- clear all ---

    @Benchmark
    public void benchmarkBitSet_ClearAll(BitSetState state, Blackhole blackhole) {
        state.bitSet.clear();
        blackhole.consume(state.bitSet);
    }

    @Benchmark
    public void benchmarkCompact_ClearAll(BitSetState state, Blackhole blackhole) {
        state.compactBean.clear();
        blackhole.consume(state.compactBean);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(CompactBitsHandleBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }

    // TestBean 类（从之前的代码复制，确保 HANDLE 和方法存在）
    public static class TestBean {
        private long inlineBits;
        private long[] heapArray;

        public static final WordAccessor<TestBean> ACCESSOR = new WordAccessor<TestBean>() {
            @Override
            public long getWord(TestBean instance, int wordIndex) {
                if (wordIndex == 0) return instance.inlineBits;
                long[] heap = instance.heapArray;
                if (heap == null || wordIndex - 1 >= heap.length) return 0L;
                return heap[wordIndex - 1];
            }

            @Override
            public void setWord(TestBean instance, int wordIndex, long value) {
                if (wordIndex == 0) {
                    instance.inlineBits = value;
                    return;
                }
                long[] heap = instance.heapArray;
                int heapIdx = wordIndex - 1;
                if (heap == null || heapIdx >= heap.length) {
                    if (value == 0L) return;
                    // 假设 ensureCapacity 已调用
                }
                heap[heapIdx] = value;
            }

            @Override
            public void ensureCapacity(TestBean instance, int minWordCount) {
                if (minWordCount <= 1) return;
                int minHeapLen = minWordCount - 1;
                long[] heap = instance.heapArray;
                int currLen = heap != null ? heap.length : 0;
                if (currLen >= minHeapLen) return;
                int newCap = Math.max(minHeapLen, currLen == 0 ? 1 : currLen * 2);
                long[] newHeap = new long[newCap];
                if (heap != null) System.arraycopy(heap, 0, newHeap, 0, heap.length);
                instance.heapArray = newHeap;
            }

            @Override
            public int getWordCount(TestBean instance) {
                return 1 + (instance.heapArray != null ? instance.heapArray.length : 0);
            }

            @Override
            public void trim(TestBean instance) {
                long[] heap = instance.heapArray;
                if (heap == null) return;
                int n = heap.length;
                while (n > 0 && heap[n - 1] == 0L) n--;
                if (n == 0) {
                    instance.heapArray = null;
                } else if (n < heap.length) {
                    instance.heapArray = Arrays.copyOf(heap, n);
                }
            }
        };

        public static final CompactBitsHandle<TestBean> HANDLE = new CompactBitsHandle<>(ACCESSOR);
/*
        public TestBean clear() {
            HANDLE.clear(this);
            return this;
        }*/

        public TestBean set(int bit) {
            HANDLE.set(this, bit);
            return this;
        }

        public TestBean set(int from, int to) {
            HANDLE.set(this, from, to);
            return this;
        }

        public TestBean clear(int bit) {
            HANDLE.clear(this, bit);
            return this;
        }

        public TestBean clear(int from, int to) {
            HANDLE.clear(this, from, to);
            return this;
        }

        public boolean get(int bit) {
            return HANDLE.get(this, bit);
        }

        public int cardinality() {
            return HANDLE.cardinality(this);
        }

        public int length() {
            return HANDLE.length(this);
        }

        public boolean isEmpty() {
            return HANDLE.isEmpty(this);
        }

        public long[] toLongArray() {
            return HANDLE.toLongArray(this);
        }

        public int nextSetBit(int fromIndex) {
            return HANDLE.nextSetBit(this, fromIndex);
        }

        public int nextClearBit(int fromIndex) {
            return HANDLE.nextClearBit(this, fromIndex);
        }

        public void fill(long[] longs) {
            HANDLE.fill(this, longs);
        }

        public int size() {
            return HANDLE.size(this);
        }

        public void clear() {
            HANDLE.clear(this);
        }
    }
}