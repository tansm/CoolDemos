package org.example

import org.junit.Assert.*
import java.nio.ByteBuffer
import org.junit.Before
import org.junit.Test

class FieldTest {

    private lateinit var buffer: ByteBuffer

    @Before
    fun setUp() {
        // 创建一个足够大的 ByteBuffer 用于测试
        buffer = ByteBuffer.allocate(64)
    }

    @Test
    fun testBooleanField() {
        val booleanField = BooleanField()
        booleanField.setOffset(5, 2)

        // 设置值
        booleanField.set(buffer, true)
        assertEquals(true, booleanField.get(buffer))

        booleanField.set(buffer, false)
        assertEquals(false, booleanField.get(buffer))
    }

    @Test
    fun testByteField() {
        val byteField = ByteField()
        byteField.setOffset(9, 0)

        // 设置值
        byteField.set(buffer, 1.toByte())
        assertEquals(1.toByte(), byteField.get(buffer))

        byteField.set(buffer, (-1).toByte())
        assertEquals((-1).toByte(), byteField.get(buffer))
    }

    @Test
    fun testShortField() {
        val shortField = ShortField()
        shortField.setOffset(10, 0)

        // 设置值
        shortField.set(buffer, 1.toShort())
        assertEquals(1.toShort(), shortField.get(buffer))

        shortField.set(buffer, (-1).toShort())
        assertEquals((-1).toShort(), shortField.get(buffer))
    }

    @Test
    fun testIntField() {
        val intField = IntField()
        intField.setOffset(12, 0)

        // 设置值
        intField.set(buffer, 1)
        assertEquals(1, intField.get(buffer))

        intField.set(buffer, -1)
        assertEquals(-1, intField.get(buffer))

        // 测试正数的最大值
        intField.set(buffer, Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, intField.get(buffer))

        // 测试负数的最小值
        intField.set(buffer, Int.MIN_VALUE)
        assertEquals(Int.MIN_VALUE, intField.get(buffer))
    }

    @Test
    fun testLongField() {
        val longField = LongField()
        longField.setOffset(16, 0)

        // 设置值
        longField.set(buffer, 1L)
        assertEquals(1L, longField.get(buffer))

        longField.set(buffer, -1L)
        assertEquals(-1L, longField.get(buffer))

        longField.set(buffer, Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, longField.get(buffer))

        longField.set(buffer, Long.MIN_VALUE)
        assertEquals(Long.MIN_VALUE, longField.get(buffer))
    }

    @Test
    fun testFloatField() {
        val floatField = FloatField()
        floatField.setOffset(24, 0)

        // 设置值
        floatField.set(buffer, 1.0f)
        assertEquals(1.0f, floatField.get(buffer), 0.001f)

        floatField.set(buffer, -1.0f)
        assertEquals(-1.0f, floatField.get(buffer), 0.001f)
    }

    @Test
    fun testDoubleField() {
        val doubleField = DoubleField()
        doubleField.setOffset(28, 0)

        // 设置值
        doubleField.set(buffer, 1.0)
        assertEquals(1.0, doubleField.get(buffer), 0.001)

        doubleField.set(buffer, -1.0)
        assertEquals(-1.0, doubleField.get(buffer), 0.001)
    }
}