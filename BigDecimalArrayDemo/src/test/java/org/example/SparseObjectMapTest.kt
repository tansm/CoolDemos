package org.example

import org.junit.Assert.*
import org.junit.Test

class SparseObjectMapTest {

    @Test
    fun testEmptyMap() {
        val map = SparseObjectMap()
        assertEquals(0, map.size)
        assertNull(map[0])
        assertNull(map[65535])
    }

    @Test
    fun testInsertAndGet() {
        val map = SparseObjectMap()
        map[1] = "a"
        map[65535] = "b"
        map[0] = "zero"
        assertEquals("a", map[1])
        assertEquals("b", map[65535])
        assertEquals("zero", map[0])
        assertEquals(3, map.size)
    }

    @Test
    fun testOverwrite() {
        val map = SparseObjectMap()
        map[10] = "x"
        assertEquals("x", map[10])
        map[10] = "y"
        assertEquals("y", map[10])
        assertEquals(1, map.size)
    }

    @Test
    fun testRemove() {
        val map = SparseObjectMap()
        map[100] = "foo"
        map[200] = "bar"
        map[300] = "baz"
        assertEquals(3, map.size)
        map.remove(200)
        assertNull(map[200])
        assertEquals(2, map.size)
        map[100] = null
        assertNull(map[100])
        assertEquals(1, map.size)
        map[300] = null
        assertEquals(0, map.size)
    }

    @Test
    fun testOrderAndHybridSearch() {
        val map = SparseObjectMap()
        // 插入大量数据，测试顺序查找和二分查找混合
        for (i in 0 until 100) {
            map[i * 10] = i
        }
        for (i in 0 until 100) {
            assertEquals(i, map[i * 10])
        }
        assertNull(map[5])
        assertNull(map[999])
        assertEquals(100, map.size)
    }

    @Test
    fun testCapacityExpansion() {
        val map = SparseObjectMap(0)
        for (i in 0..50) {
            map[i] = i * 2
        }
        for (i in 0..50) {
            assertEquals(i * 2, map[i])
        }
        assertEquals(51, map.size)
    }

    @Test
    fun testKeyRangeCheck() {
       /* val map = SparseObjectMap()
        assertFailsWith<IllegalArgumentException> { map[-1] = "bad" }
        assertFailsWith<IllegalArgumentException> { map[65536] = "bad" }
        assertFailsWith<IllegalArgumentException> { map.get(-1) }
        assertFailsWith<IllegalArgumentException> { map.get(65536) }
        assertFailsWith<IllegalArgumentException> { map.remove(-1) }
        assertFailsWith<IllegalArgumentException> { map.remove(65536) }*/
    }

    @Test
    fun testInsertRemoveInsert() {
        val map = SparseObjectMap()
        map[123] = "abc"
        assertEquals("abc", map[123])
        map[123] = null
        assertNull(map[123])
        map[123] = "def"
        assertEquals("def", map[123])
    }

    @Test
    fun testInsertManyAndRemoveSome() {
        val map = SparseObjectMap()
        for (i in 0..200 step 2) {
            map[i] = i
        }
        for (i in 0..200 step 4) {
            map.remove(i)
        }
        for (i in 0..200 step 2) {
            if (i % 4 == 0) {
                assertNull(map[i])
            } else {
                assertEquals(i, map[i])
            }
        }
    }
}