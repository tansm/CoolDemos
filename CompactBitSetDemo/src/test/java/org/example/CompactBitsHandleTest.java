package org.example;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.BitSet;

import static org.junit.Assert.*;

public class CompactBitsHandleTest {

    // 测试类
    public static class TestBean {
        private long inlineBits;
        private long[] heapArray;

        // 在TestBean中
        private static final WordAccessor<TestBean> ACCESSOR = new WordAccessor<TestBean>() {
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
                heap[heapIdx] = value;
            }

            @Override
            public void ensureCapacity(TestBean instance, int minWordCount) {
                if (minWordCount <= 1) return; // inline覆盖word 0
                int minHeap = minWordCount - 1;
                long[] heap = instance.heapArray;
                int currHeap = heap != null ? heap.length : 0;
                if (currHeap >= minHeap) return;
                int newCap = Math.max(minHeap, currHeap == 0 ? 1 : currHeap * 2);
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
                if (n == 0) instance.heapArray = null;
                else if (n < heap.length) instance.heapArray = Arrays.copyOf(heap, n);
            }
        };

        public static final CompactBitsHandle<TestBean> HANDLE = new CompactBitsHandle<>(ACCESSOR);

        public TestBean clear() {
            HANDLE.clear(this);
            return this;
        }

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

        public void fill(long[] longs){
            HANDLE.fill(this, longs);
        }

        public int size(){
            return HANDLE.size(this);
        }
    }

    private TestBean bean;
    private CompactBitsHandle<TestBean> handle;

    @Before
    public void setUp() {
        bean = new TestBean();
        handle = TestBean.HANDLE;
    }

    // --- 基本 set/get/clear ---

    @Test
    public void testSetAndGet_inline() {
        bean.set(0);
        bean.set(31);
        bean.set(63);

        assertTrue(bean.get(0));
        assertTrue(bean.get(31));
        assertTrue(bean.get(63));
        assertFalse(bean.get(64));
    }

    @Test
    public void testSetAndGet_heap() {
        bean.set(64);
        bean.set(100);
        bean.set(127);

        assertTrue(bean.get(64));
        assertTrue(bean.get(100));
        assertTrue(bean.get(127));
        assertFalse(bean.get(128));
    }

    @Test
    public void testClearBit() {
        bean.set(5).set(70);
        assertTrue(bean.get(5));
        assertTrue(bean.get(70));

        bean.clear(5);
        assertFalse(bean.get(5));
        assertTrue(bean.get(70));

        bean.clear(70);
        assertFalse(bean.get(70));
    }

    // --- 批量操作 ---

    @Test
    public void testSetRange_inline() {
        bean.set(5, 10); // bits 5,6,7,8,9
        for (int i = 5; i < 10; i++) {
            assertTrue("bit " + i + " should be set", bean.get(i));
        }
        assertFalse(bean.get(4));
        assertFalse(bean.get(10));
    }

    @Test
    public void testSetRange_cross() {
        bean.set(60, 70); // 跨 inline 和 heap
        for (int i = 60; i < 64; i++) {
            assertTrue(bean.get(i));
        }
        for (int i = 64; i < 70; i++) {
            assertTrue(bean.get(i));
        }
    }

    @Test
    public void testSetRange_heap() {
        bean.set(100, 164);
        for (int i = 100; i < 164; i++) {
            assertTrue(bean.get(i));
        }
    }

    @Test
    public void testClearRange() {
        bean.set(0, 200);
        assertEquals(200, bean.cardinality());

        bean.clear(50, 100);
        for (int i = 0; i < 50; i++) assertTrue(bean.get(i));
        for (int i = 50; i < 100; i++) assertFalse(bean.get(i));
        for (int i = 100; i < 200; i++) assertTrue(bean.get(i));
    }

    @Test
    public void testClearAll() {
        bean.set(0, 100);
        assertFalse(bean.isEmpty());
        bean.clear();
        assertTrue(bean.isEmpty());
        assertEquals(0, bean.cardinality());
    }

    // --- 参数检查 ---

    @Test(expected = IndexOutOfBoundsException.class)
    public void testSet_negativeIndex() {
        bean.set(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testSetRange_fromGreaterThanTo() {
        bean.set(10, 5);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testSetRange_negativeFrom() {
        bean.set(-1, 10);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testSetRange_negativeTo() {
        bean.set(10, -5);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGet_negativeIndex() {
        bean.get(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testNextSetBit_negative() {
        bean.nextSetBit(-1);
    }

    // --- cardinality / length / isEmpty ---

    @Test
    public void testCardinality() {
        bean.set(0);
        bean.set(1);
        bean.set(64);
        assertEquals(3, bean.cardinality());

        bean.clear(1);
        assertEquals(2, bean.cardinality());
    }

    @Test
    public void testLength() {
        bean.set(0);
        assertEquals(1, bean.length());

        bean.set(10);
        assertEquals(11, bean.length());

        bean.set(100);
        assertEquals(101, bean.length());

        bean.clear(100);
        assertEquals(11, bean.length());

        bean.clear();
        assertEquals(0, bean.length());
    }

    @Test
    public void testIsEmpty() {
        assertTrue(bean.isEmpty());
        bean.set(0);
        assertFalse(bean.isEmpty());
        bean.clear();
        assertTrue(bean.isEmpty());
    }

    @Test
    public void testSize() {
        assertEquals(64, bean.size()); // inline + 0 heap
        bean.set(100);
        assertEquals(64 + 64, bean.size()); // heap.length=1 → +64
        bean.set(200);
        assertTrue(bean.size() >= 192); // 至少支持 200
    }

    // --- fill / toLongArray ---

    @Test
    public void testFill() {
        long[] src = {0x123456789ABCDEF0L, 0xFEDCBA9876543210L, 0x0123456789ABCDEFL};
        bean.fill(src);

        assertEquals(0x123456789ABCDEF0L, bean.inlineBits); // inline
        assertNotNull(bean.heapArray);
        assertEquals(2, bean.heapArray.length);
        assertEquals(0xFEDCBA9876543210L, bean.heapArray[0]);
        assertEquals(0x0123456789ABCDEFL, bean.heapArray[1]);
    }

    @Test
    public void testToLongArray() {
        bean.set(0);
        bean.set(64);
        bean.set(128);

        long[] arr = bean.toLongArray();
        assertEquals(3, arr.length);
        assertEquals(1L, arr[0]);           // inline
        assertEquals(1L, arr[1]);           // heap[0]
        assertEquals(1L, arr[2]);           // heap[1]
    }

    // --- nextSetBit / nextClearBit ---

    @Test
    public void testNextSetBit() {
        bean.set(5);
        bean.set(10);
        bean.set(100);

        assertEquals(5, bean.nextSetBit(0));
        assertEquals(5, bean.nextSetBit(5));
        assertEquals(10, bean.nextSetBit(6));
        assertEquals(100, bean.nextSetBit(50));
        assertEquals(-1, bean.nextSetBit(200));
    }

    @Test
    public void testNextClearBit() {
        // 全 0
        assertEquals(0, bean.nextClearBit(0));

        // 设置一些位
        bean.set(0, 10); // 0~9
        assertEquals(10, bean.nextClearBit(0));
        assertEquals(10, bean.nextClearBit(5));
        assertEquals(10, bean.nextClearBit(10)); // 10 is clear
        assertEquals(11, bean.nextClearBit(11));
    }

    // --- 与 BitSet 对比测试 ---

    @Test
    public void testVsBitSet_setRange() {
        BitSet bs = new BitSet();
        TestBean cb = new TestBean();

        // 设置相同范围
        bs.set(50, 100);
        cb.set(50, 100);

        for (int i = 0; i < 150; i++) {
            assertEquals("bit " + i, bs.get(i), cb.get(i));
        }
    }

    @Test
    public void testVsBitSet_clearRange() {
        BitSet bs = new BitSet();
        TestBean cb = new TestBean();

        bs.set(0, 200);
        cb.set(0, 200);

        bs.clear(50, 100);
        cb.clear(50, 100);

        for (int i = 0; i < 200; i++) {
            assertEquals("bit " + i, bs.get(i), cb.get(i));
        }
    }

    @Test
    public void testVsBitSet_cardinality() {
        BitSet bs = new BitSet();
        TestBean cb = new TestBean();

        bs.set(0, 100);
        bs.set(200, 250);
        cb.set(0, 100);
        cb.set(200, 250);

        assertEquals(bs.cardinality(), cb.cardinality());
    }

    @Test
    public void testVsBitSet_nextSetBit() {
        BitSet bs = new BitSet();
        TestBean cb = new TestBean();

        bs.set(10);
        bs.set(50);
        bs.set(100);
        cb.set(10);
        cb.set(50);
        cb.set(100);

        assertEquals(bs.nextSetBit(0), cb.nextSetBit(0));
        assertEquals(bs.nextSetBit(11), cb.nextSetBit(11));
        assertEquals(bs.nextSetBit(60), cb.nextSetBit(60));
        assertEquals(bs.nextSetBit(200), cb.nextSetBit(200));
    }

    @Test
    public void testVsBitSet_nextClearBit() {
        BitSet bs = new BitSet();
        TestBean cb = new TestBean();

        bs.set(0, 20);  // 0~19
        bs.set(50, 70); // 50~69
        cb.set(0, 20);
        cb.set(50, 70);

        assertEquals(bs.nextClearBit(0), cb.nextClearBit(0));
        assertEquals(bs.nextClearBit(20), cb.nextClearBit(20));
        assertEquals(bs.nextClearBit(50), cb.nextClearBit(50));
    }

    // --- fill 对比测试 ---

    @Test
    public void testFill_vsBitSet_valueOf() {
        long[] longs = {0x123456789ABCDEF0L, 0xFEDCBA9876543210L};

        BitSet bs = BitSet.valueOf(longs);
        TestBean cb = new TestBean();
        cb.fill(longs);

        for (int i = 0; i < 128; i++) {
            assertEquals("bit " + i, bs.get(i), cb.get(i));
        }
    }
}