package org.example

import org.junit.Assert.*
import java.nio.ByteBuffer
import org.junit.Before
import org.junit.Test

class PropertyAccessorTest {

    private lateinit var buffer: ByteBuffer

    @Before
    fun setUp() {
        // 创建一个足够大的 ByteBuffer 用于测试
        buffer = ByteBuffer.allocate(64)
    }
    
    @Test
    fun testBooleanPropertyAccessorNonNullableDefaultFalse() {
        val accessor = BooleanPropertyAccessor(nullable = false, defaultValue = false)

        assertEquals(false, accessor.defaultValue)

        // 获取字段并分配偏移量
        val fields = accessor.getFields()
        assertEquals(1, fields.size) // 不可为null，不需要标志位
        LayoutManager.assignOffsets(fields)

        // 测试默认值
        assertFalse(accessor.getBoolean(buffer))
        assertEquals(false, accessor.get(buffer)) // get方法返回原始类型包装类

        // 设置和获取值
        accessor.setBoolean(buffer, true)
        assertTrue(accessor.getBoolean(buffer))
        assertEquals(true, accessor.get(buffer))

        accessor.setBoolean(buffer, false)
        assertFalse(accessor.getBoolean(buffer))
        assertEquals(false, accessor.get(buffer))

        accessor.set(buffer, true)
        assertTrue(accessor.getBoolean(buffer))
        assertEquals(true, accessor.get(buffer))

        // 尝试设置null值
        accessor.set(buffer, null)
        assertFalse(accessor.getBoolean(buffer))
        assertEquals(false, accessor.get(buffer))
    }

    @Test
    fun testBooleanPropertyAccessorNonNullableDefaultTrue() {
        val accessor = BooleanPropertyAccessor(nullable = false, defaultValue = true)

        assertEquals(true, accessor.defaultValue)

        // 获取字段并分配偏移量
        val fields = accessor.getFields()
        assertEquals(2, fields.size) // 不可为null，但需要标志位
        LayoutManager.assignOffsets(fields)

        // 测试默认值
        assertTrue(accessor.getBoolean(buffer))
        assertEquals(true, accessor.get(buffer)) // get方法返回原始类型包装类

        // 设置和获取值
        accessor.setBoolean(buffer, false)
        assertFalse(accessor.getBoolean(buffer))
        assertEquals(false, accessor.get(buffer))

        accessor.setBoolean(buffer, true)
        assertTrue(accessor.getBoolean(buffer))
        assertEquals(true, accessor.get(buffer))

        accessor.set(buffer, false)
        assertFalse(accessor.getBoolean(buffer))
        assertEquals(false, accessor.get(buffer))

        accessor.set(buffer, null)
        assertTrue(accessor.getBoolean(buffer))
        assertEquals(true, accessor.get(buffer))
    }

    @Test
    fun testBooleanPropertyAccessorNullable() {
        val accessor = BooleanPropertyAccessor(nullable = true, defaultValue = false)
        assertNull(accessor.defaultValue)

        // 获取字段并分配偏移量
        val fields = accessor.getFields()
        assertEquals(2, fields.size) // 可为null，需要标志位
        LayoutManager.assignOffsets(fields)

        // 测试默认值
        assertNull(accessor.get(buffer))
        assertFalse(accessor.getBoolean(buffer)) // 允许为null时，调用getBoolean返回false

        // 设置和获取值
        accessor.setBoolean(buffer, true)
        assertTrue(accessor.getBoolean(buffer))
        assertEquals(true, accessor.get(buffer))

        accessor.setBoolean(buffer, false)
        assertFalse(accessor.getBoolean(buffer))
        assertEquals(false, accessor.get(buffer))

        accessor.set(buffer, true)
        assertTrue(accessor.getBoolean(buffer))
        assertEquals(true, accessor.get(buffer))

        // 设置为null
        accessor.set(buffer, null)
        assertNull(accessor.get(buffer))
        assertFalse(accessor.getBoolean(buffer))

        // 再次设置值
        accessor.set(buffer, false)
        assertFalse(accessor.getBoolean(buffer))
        assertEquals(false, accessor.get(buffer))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBooleanPropertyAccessorNullableCtorError(){
        BooleanPropertyAccessor(nullable = true, defaultValue = true)
    }

    @Test
    fun testBytePropertyAccessorNonNullable() {
        val accessor = BytePropertyAccessor(nullable = false, defaultValue = 0)

        // 检查 defaultValue 属性
        assertEquals(0.toByte(), accessor.defaultValue)

        // 获取字段并分配偏移量
        val fields = accessor.getFields()
        assertEquals(1, fields.size) // 不可为null，且缺省值为0，不需要标志位
        LayoutManager.assignOffsets(fields)

        // 测试默认值
        assertEquals(0.toByte(), accessor.get(buffer))
        assertEquals(0.toByte(), accessor.getByte(buffer))

        // 设置和获取值
        accessor.setByte(buffer, 42)
        assertEquals(42.toByte(), accessor.get(buffer))
        assertEquals(42.toByte(), accessor.getByte(buffer))

        // 测试最大值
        accessor.setByte(buffer, Byte.MAX_VALUE)
        assertEquals(Byte.MAX_VALUE, accessor.get(buffer))
        assertEquals(Byte.MAX_VALUE, accessor.getByte(buffer))

        // 测试最小值
        accessor.setByte(buffer, Byte.MIN_VALUE)
        assertEquals(Byte.MIN_VALUE, accessor.get(buffer))
        assertEquals(Byte.MIN_VALUE, accessor.getByte(buffer))

        // 虽然 nullable = false, 但仍然支持设置为 null， 类似 reset 效果
        accessor.set(buffer, null)
        assertEquals(0.toByte(), accessor.get(buffer))
        assertEquals(0.toByte(), accessor.getByte(buffer))
    }

    @Test
    fun testBytePropertyAccessorNullable() {
        val accessor = BytePropertyAccessor(nullable = true, defaultValue = 0)

        // 检查 defaultValue 属性
        assertNull(accessor.defaultValue)

        // 获取字段并分配偏移量
        val fields = accessor.getFields()
        assertEquals(2, fields.size)
        LayoutManager.assignOffsets(fields)

        // 测试默认值（应为 null）
        assertNull(accessor.get(buffer))
        assertEquals(0.toByte(), accessor.getByte(buffer)) // 允许为null时，调用 getByte 返回0

        // 设置和获取值
        accessor.set(buffer, 42.toByte())
        assertEquals(42.toByte(), accessor.get(buffer))
        assertEquals(42.toByte(), accessor.getByte(buffer))

        accessor.setByte(buffer, -43)
        assertEquals((-43).toByte(), accessor.get(buffer))
        assertEquals((-43).toByte(), accessor.getByte(buffer))

        // 设置为 null
        accessor.set(buffer, null)
        assertNull(accessor.get(buffer))
        assertEquals(0.toByte(), accessor.getByte(buffer))

        // 再次设置值
        accessor.set(buffer, 123.toByte())
        assertEquals(123.toByte(), accessor.get(buffer))
        assertEquals(123.toByte(), accessor.getByte(buffer))
    }

    @Test
    fun testBytePropertyAccessorNonNullableWithNonZeroDefaultValue() {
        val nonZeroDefaultValue: Byte = 123
        val accessor = BytePropertyAccessor(nullable = false, defaultValue = nonZeroDefaultValue)

        // 检查 defaultValue 属性
        assertEquals(nonZeroDefaultValue, accessor.defaultValue)

        // 获取字段并分配偏移量
        val fields = accessor.getFields()
        assertEquals(2, fields.size)
        LayoutManager.assignOffsets(fields)

        // 测试默认值
        assertEquals(nonZeroDefaultValue, accessor.get(buffer))
        assertEquals(nonZeroDefaultValue, accessor.getByte(buffer))

        // 设置和获取值
        accessor.setByte(buffer, 45)
        assertEquals(45.toByte(), accessor.get(buffer))
        assertEquals(45.toByte(), accessor.getByte(buffer))

        // 重新设置为默认值
        accessor.setByte(buffer, nonZeroDefaultValue)
        assertEquals(nonZeroDefaultValue, accessor.get(buffer))
        assertEquals(nonZeroDefaultValue, accessor.getByte(buffer))

        // 内部存储不太一样，但外部表现为 reset
        accessor.set(buffer, null)
        assertEquals(nonZeroDefaultValue, accessor.get(buffer))
        assertEquals(nonZeroDefaultValue, accessor.getByte(buffer))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBytePropertyAccessorNullableCtorError() {
        BytePropertyAccessor(nullable = true, defaultValue = 123)
    }

    @Test
    fun testShortPropertyAccessorNonNullable() {
        val accessor = ShortPropertyAccessor(nullable = false, defaultValue = 0)

        // 检查 defaultValue 属性
        assertEquals(0.toShort(), accessor.defaultValue)

        // 获取字段并分配偏移量
        val fields = accessor.getFields()
        assertEquals(1, fields.size) // 不可为null，且缺省值为0，不需要标志位
        LayoutManager.assignOffsets(fields)

        // 测试默认值
        assertEquals(0.toShort(), accessor.get(buffer))
        assertEquals(0.toShort(), accessor.getShort(buffer))

        // 设置和获取值
        accessor.setShort(buffer, 42)
        assertEquals(42.toShort(), accessor.get(buffer))
        assertEquals(42.toShort(), accessor.getShort(buffer))

        // 测试最大值
        accessor.setShort(buffer, Short.MAX_VALUE)
        assertEquals(Short.MAX_VALUE, accessor.get(buffer))
        assertEquals(Short.MAX_VALUE, accessor.getShort(buffer))

        // 测试最小值
        accessor.setShort(buffer, Short.MIN_VALUE)
        assertEquals(Short.MIN_VALUE, accessor.get(buffer))
        assertEquals(Short.MIN_VALUE, accessor.getShort(buffer))

        // 虽然 nullable = false, 但仍然支持设置为 null， 类似 reset 效果
        accessor.set(buffer, null)
        assertEquals(0.toShort(), accessor.get(buffer))
        assertEquals(0.toShort(), accessor.getShort(buffer))
    }

    @Test
    fun testShortPropertyAccessorNullable() {
        val accessor = ShortPropertyAccessor(nullable = true, defaultValue = 0)

        // 检查 defaultValue 属性
        assertNull(accessor.defaultValue)

        // 获取字段并分配偏移量
        val fields = accessor.getFields()
        assertEquals(2, fields.size)
        LayoutManager.assignOffsets(fields)

        // 测试默认值（应为 null）
        assertNull(accessor.get(buffer))
        assertEquals(0.toShort(), accessor.getShort(buffer)) // 允许为null时，调用 getShort 返回0

        // 设置和获取值
        accessor.set(buffer, 42.toShort())
        assertEquals(42.toShort(), accessor.get(buffer))
        assertEquals(42.toShort(), accessor.getShort(buffer))

        accessor.setShort(buffer, -43)
        assertEquals((-43).toShort(), accessor.get(buffer))
        assertEquals((-43).toShort(), accessor.getShort(buffer))

        // 设置为 null
        accessor.set(buffer, null)
        assertNull(accessor.get(buffer))
        assertEquals(0.toShort(), accessor.getShort(buffer))

        // 再次设置值
        accessor.set(buffer, 123.toShort())
        assertEquals(123.toShort(), accessor.get(buffer))
        assertEquals(123.toShort(), accessor.getShort(buffer))
    }

    @Test
    fun testShortPropertyAccessorNonNullableWithNonZeroDefaultValue() {
        val nonZeroDefaultValue: Short = 123
        val accessor = ShortPropertyAccessor(nullable = false, defaultValue = nonZeroDefaultValue)

        // 检查 defaultValue 属性
        assertEquals(nonZeroDefaultValue, accessor.defaultValue)

        // 获取字段并分配偏移量
        val fields = accessor.getFields()
        assertEquals(2, fields.size)
        LayoutManager.assignOffsets(fields)

        // 测试默认值
        assertEquals(nonZeroDefaultValue, accessor.get(buffer))
        assertEquals(nonZeroDefaultValue, accessor.getShort(buffer))

        // 设置和获取值
        accessor.setShort(buffer, 45)
        assertEquals(45.toShort(), accessor.get(buffer))
        assertEquals(45.toShort(), accessor.getShort(buffer))

        accessor.set(buffer, (-46).toShort())
        assertEquals((-46).toShort(), accessor.get(buffer))
        assertEquals((-46).toShort(), accessor.getShort(buffer))

        // 重新设置为默认值
        accessor.setShort(buffer, nonZeroDefaultValue)
        assertEquals(nonZeroDefaultValue, accessor.get(buffer))
        assertEquals(nonZeroDefaultValue, accessor.getShort(buffer))

        // 内部存储不太一样，但外部表现为 reset
        accessor.set(buffer, null)
        assertEquals(nonZeroDefaultValue, accessor.get(buffer))
        assertEquals(nonZeroDefaultValue, accessor.getShort(buffer))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testShortPropertyAccessorNullableCtorError() {
        ShortPropertyAccessor(nullable = true, defaultValue = 123)
    }

    @Test
    fun testIntPropertyAccessorNonNullable() {
        val accessor = IntPropertyAccessor(nullable = false, defaultValue = 0)
        assertEquals(0, accessor.defaultValue)

        // 获取字段并分配偏移量
        val fields = accessor.getFields()
        assertEquals(1, fields.size) // 不可为null，且缺省值为0，不需要标志位
        LayoutManager.assignOffsets(fields)

        // 测试默认值
        assertEquals(0, accessor.get(buffer))
        assertEquals(0, accessor.getInt(buffer))

        // 设置和获取值
        accessor.setInt(buffer, 42)
        assertEquals(42, accessor.get(buffer))
        assertEquals(42, accessor.getInt(buffer))

        accessor.set(buffer, -43) // 装箱版本的 set
        assertEquals(-43, accessor.get(buffer))
        assertEquals(-43, accessor.getInt(buffer))

        // 测试正数的最大值
        accessor.setInt(buffer, Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, accessor.get(buffer))
        assertEquals(Int.MAX_VALUE, accessor.getInt(buffer))

        // 测试负数的最小值
        accessor.setInt(buffer, Int.MIN_VALUE)
        assertEquals(Int.MIN_VALUE, accessor.get(buffer))
        assertEquals(Int.MIN_VALUE, accessor.getInt(buffer))

        // 虽然 nullable = false, 但仍然支持设置为 null， 类似 reset 效果
        accessor.set(buffer, null)
        assertEquals(0, accessor.get(buffer))
        assertEquals(0, accessor.getInt(buffer))
    }

    @Test
    fun testIntPropertyAccessorNullable() {
        val accessor = IntPropertyAccessor(nullable = true, defaultValue = 0)
        assertNull(accessor.defaultValue)

        // 获取字段并分配偏移量
        val fields = accessor.getFields()
        assertEquals(2, fields.size)
        LayoutManager.assignOffsets(fields)

        // 测试默认值（应为 null）
        assertNull(accessor.get(buffer))
        assertEquals(0, accessor.getInt(buffer)) // 允许为null时，调用 getInt 返回0

        // 设置和获取值
        accessor.set(buffer, 42)
        assertEquals(42, accessor.get(buffer))
        assertEquals(42, accessor.getInt(buffer))

        accessor.set(buffer, -43)
        assertEquals(-43, accessor.get(buffer))
        assertEquals(-43, accessor.getInt(buffer))

        // 设置为 null
        accessor.set(buffer, null)
        assertNull(accessor.get(buffer))
        assertEquals(0, accessor.getInt(buffer))

        // 再次设置值
        accessor.set(buffer, 123)
        assertEquals(123, accessor.get(buffer))
        assertEquals(123, accessor.getInt(buffer))
    }

    @Test
    fun testIntPropertyAccessorNonNullableWithNonZeroDefaultValue() {
        val nonZeroDefaultValue = 123
        val accessor = IntPropertyAccessor(nullable = false, defaultValue = nonZeroDefaultValue)
        assertEquals(nonZeroDefaultValue, accessor.defaultValue)

        // 获取字段并分配偏移量
        val fields = accessor.getFields()
        assertEquals(2, fields.size)
        LayoutManager.assignOffsets(fields)

        // 测试默认值
        assertEquals(nonZeroDefaultValue, accessor.get(buffer))
        assertEquals(nonZeroDefaultValue, accessor.getInt(buffer))

        // 设置和获取值
        accessor.setInt(buffer, 456)
        assertEquals(456, accessor.get(buffer))
        assertEquals(456, accessor.getInt(buffer))

        accessor.set(buffer, -456)
        assertEquals(-456, accessor.get(buffer))
        assertEquals(-456, accessor.getInt(buffer))

        // 重新设置为默认值
        accessor.setInt(buffer, nonZeroDefaultValue)
        assertEquals(nonZeroDefaultValue, accessor.get(buffer))
        assertEquals(nonZeroDefaultValue, accessor.getInt(buffer))

        // 内部存储不太一样，但外部表现为 reset
        accessor.set(buffer, null)
        assertEquals(nonZeroDefaultValue, accessor.get(buffer))
        assertEquals(nonZeroDefaultValue, accessor.getInt(buffer))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testIntPropertyAccessorNullableCtorError(){
        IntPropertyAccessor(nullable = true, defaultValue = 123)
    }

    @Test
    fun testLongPropertyAccessorNonNullable() {
        val accessor = LongPropertyAccessor(nullable = false, defaultValue = 0L)
        assertEquals(0L, accessor.defaultValue)

        // 获取字段并分配偏移量
        val fields = accessor.getFields()
        assertEquals(1, fields.size) // 不可为null，且缺省值为0，不需要标志位
        LayoutManager.assignOffsets(fields)

        // 测试默认值
        assertEquals(0L, accessor.get(buffer))
        assertEquals(0L, accessor.getLong(buffer))

        // 设置和获取值
        accessor.setLong(buffer, 42L)
        assertEquals(42L, accessor.get(buffer))
        assertEquals(42L, accessor.getLong(buffer))

        accessor.set(buffer, -43L)
        assertEquals(-43L, accessor.get(buffer))
        assertEquals(-43L, accessor.getLong(buffer))

        // 测试最大值
        accessor.setLong(buffer, Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, accessor.get(buffer))
        assertEquals(Long.MAX_VALUE, accessor.getLong(buffer))

        // 测试最小值
        accessor.setLong(buffer, Long.MIN_VALUE)
        assertEquals(Long.MIN_VALUE, accessor.get(buffer))
        assertEquals(Long.MIN_VALUE, accessor.getLong(buffer))

        // 虽然 nullable = false, 但仍然支持设置为 null， 类似 reset 效果
        accessor.set(buffer, null)
        assertEquals(0L, accessor.get(buffer))
        assertEquals(0L, accessor.getLong(buffer))
    }

    @Test
    fun testLongPropertyAccessorNullable() {
        val accessor = LongPropertyAccessor(nullable = true, defaultValue = 0L)
        assertNull(accessor.defaultValue)

        // 获取字段并分配偏移量
        val fields = accessor.getFields()
        assertEquals(2, fields.size)
        LayoutManager.assignOffsets(fields)

        // 测试默认值（应为 null）
        assertNull(accessor.get(buffer))
        assertEquals(0L, accessor.getLong(buffer)) // 允许为null时，调用 getLong 返回0

        // 设置和获取值
        accessor.set(buffer, 42L)
        assertEquals(42L, accessor.get(buffer))
        assertEquals(42L, accessor.getLong(buffer))

        accessor.setLong(buffer, -43L)
        assertEquals(-43L, accessor.get(buffer))
        assertEquals(-43L, accessor.getLong(buffer))

        // 设置为 null
        accessor.set(buffer, null)
        assertNull(accessor.get(buffer))
        assertEquals(0L, accessor.getLong(buffer))

        // 再次设置值
        accessor.set(buffer, 123L)
        assertEquals(123L, accessor.get(buffer))
        assertEquals(123L, accessor.getLong(buffer))
    }

    @Test
    fun testLongPropertyAccessorNonNullableWithNonZeroDefaultValue() {
        val nonZeroDefaultValue = 123L
        val accessor = LongPropertyAccessor(nullable = false, defaultValue = nonZeroDefaultValue)
        assertEquals(nonZeroDefaultValue, accessor.defaultValue)

        // 获取字段并分配偏移量
        val fields = accessor.getFields()
        assertEquals(2, fields.size)
        LayoutManager.assignOffsets(fields)

        // 测试默认值
        assertEquals(nonZeroDefaultValue, accessor.get(buffer))
        assertEquals(nonZeroDefaultValue, accessor.getLong(buffer))

        // 设置和获取值
        accessor.setLong(buffer, 456L)
        assertEquals(456L, accessor.get(buffer))
        assertEquals(456L, accessor.getLong(buffer))

        accessor.set(buffer, -456L)
        assertEquals(-456L, accessor.get(buffer))
        assertEquals(-456L, accessor.getLong(buffer))

        // 重新设置为默认值
        accessor.setLong(buffer, nonZeroDefaultValue)
        assertEquals(nonZeroDefaultValue, accessor.get(buffer))
        assertEquals(nonZeroDefaultValue, accessor.getLong(buffer))

        // 内部存储不太一样，但外部表现为 reset
        accessor.set(buffer, null)
        assertEquals(nonZeroDefaultValue, accessor.get(buffer))
        assertEquals(nonZeroDefaultValue, accessor.getLong(buffer))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testLongPropertyAccessorNullableCtorError() {
        LongPropertyAccessor(nullable = true, defaultValue = 123L)
    }

    @Test
    fun testFloatPropertyAccessorNonNullable() {
        val accessor = FloatPropertyAccessor(nullable = false, defaultValue = 0.0f)
        assertEquals(0.0f, accessor.defaultValue)

        // 获取字段并分配偏移量
        val fields = accessor.getFields()
        assertEquals(1, fields.size)
        LayoutManager.assignOffsets(fields)

        // 测试默认值
        assertEquals(0.0f, accessor.get(buffer))
        assertEquals(0.0f, accessor.getFloat(buffer))

        // 设置和获取值
        accessor.setFloat(buffer, 42.5f)
        assertEquals(42.5f, accessor.get(buffer))
        assertEquals(42.5f, accessor.getFloat(buffer))

        // 测试最大值
        accessor.setFloat(buffer, Float.MAX_VALUE)
        assertEquals(Float.MAX_VALUE, accessor.get(buffer))
        assertEquals(Float.MAX_VALUE, accessor.getFloat(buffer))

        // 测试最小值
        accessor.setFloat(buffer, -Float.MAX_VALUE)
        assertEquals(-Float.MAX_VALUE, accessor.get(buffer))
        assertEquals(-Float.MAX_VALUE, accessor.getFloat(buffer))

        // 虽然 nullable = false, 但仍然支持设置为 null， 类似 reset 效果
        accessor.set(buffer, null)
        assertEquals(0.0f, accessor.get(buffer))
        assertEquals(0.0f, accessor.getFloat(buffer))
    }

    @Test
    fun testFloatPropertyAccessorNullable() {
        val accessor = FloatPropertyAccessor(nullable = true, defaultValue = 0.0f)
        assertNull(accessor.defaultValue)

        // 获取字段并分配偏移量
        val fields = accessor.getFields()
        assertEquals(2, fields.size)
        LayoutManager.assignOffsets(fields)

        // 测试默认值（应为 null）
        assertNull(accessor.get(buffer))
        assertEquals(0.0f, accessor.getFloat(buffer))

        // 设置和获取值
        accessor.set(buffer, 42.5f)
        assertEquals(42.5f, accessor.get(buffer))
        assertEquals(42.5f, accessor.getFloat(buffer))

        accessor.setFloat(buffer, -43.5f)
        assertEquals(-43.5f, accessor.get(buffer))
        assertEquals(-43.5f, accessor.getFloat(buffer))

        // 设置为 null
        accessor.set(buffer, null)
        assertNull(accessor.get(buffer))
        assertEquals(0.0f, accessor.getFloat(buffer))

        // 再次设置值
        accessor.set(buffer, 123.25f)
        assertEquals(123.25f, accessor.get(buffer))
        assertEquals(123.25f, accessor.getFloat(buffer))
    }

    @Test
    fun testFloatPropertyAccessorNonNullableWithNonZeroDefaultValue() {
        val nonZeroDefaultValue = 123.5f
        val accessor = FloatPropertyAccessor(nullable = false, defaultValue = nonZeroDefaultValue)
        assertEquals(nonZeroDefaultValue, accessor.defaultValue)

        // 获取字段并分配偏移量
        val fields = accessor.getFields()
        assertEquals(2, fields.size)
        LayoutManager.assignOffsets(fields)

        // 测试默认值
        assertEquals(nonZeroDefaultValue, accessor.get(buffer))
        assertEquals(nonZeroDefaultValue, accessor.getFloat(buffer))

        // 设置和获取值
        accessor.setFloat(buffer, 456.5f)
        assertEquals(456.5f, accessor.get(buffer))
        assertEquals(456.5f, accessor.getFloat(buffer))

        accessor.set(buffer, -456.5f)
        assertEquals(-456.5f, accessor.get(buffer))
        assertEquals(-456.5f, accessor.getFloat(buffer))

        // 重新设置为默认值
        accessor.setFloat(buffer, nonZeroDefaultValue)
        assertEquals(nonZeroDefaultValue, accessor.get(buffer))
        assertEquals(nonZeroDefaultValue, accessor.getFloat(buffer))

        // 内部存储不太一样，但外部表现为 reset
        accessor.set(buffer, null)
        assertEquals(nonZeroDefaultValue, accessor.get(buffer))
        assertEquals(nonZeroDefaultValue, accessor.getFloat(buffer))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFloatPropertyAccessorNullableCtorError() {
        FloatPropertyAccessor(nullable = true, defaultValue = 123.5f)
    }

    @Test
    fun testDoublePropertyAccessorNonNullable() {
        val accessor = DoublePropertyAccessor(nullable = false, defaultValue = 0.0)
        assertEquals(0.0, accessor.defaultValue as Double, 1e-10)

        // 获取字段并分配偏移量
        val fields = accessor.getFields()
        assertEquals(1, fields.size)
        LayoutManager.assignOffsets(fields)

        // 测试默认值
        assertEquals(0.0, accessor.get(buffer) as Double, 1e-10)
        assertEquals(0.0, accessor.getDouble(buffer), 1e-10)

        // 设置和获取值
        accessor.setDouble(buffer, 42.5)
        assertEquals(42.5, accessor.get(buffer) as Double, 1e-10)
        assertEquals(42.5, accessor.getDouble(buffer), 1e-10)

        // 测试最大值
        accessor.setDouble(buffer, Double.MAX_VALUE)
        assertEquals(Double.MAX_VALUE, accessor.get(buffer) as Double, 1e-10)
        assertEquals(Double.MAX_VALUE, accessor.getDouble(buffer), 1e-10)

        // 测试最小值
        accessor.setDouble(buffer, -Double.MAX_VALUE)
        assertEquals(-Double.MAX_VALUE, accessor.get(buffer) as Double, 1e-10)
        assertEquals(-Double.MAX_VALUE, accessor.getDouble(buffer), 1e-10)

        // 虽然 nullable = false, 但仍然支持设置为 null， 类似 reset 效果
        accessor.set(buffer, null)
        assertEquals(0.0, accessor.get(buffer) as Double, 1e-10)
        assertEquals(0.0, accessor.getDouble(buffer), 1e-10)
    }

    @Test
    fun testDoublePropertyAccessorNullable() {
        val accessor = DoublePropertyAccessor(nullable = true, defaultValue = 0.0)
        assertNull(accessor.defaultValue)

        // 获取字段并分配偏移量
        val fields = accessor.getFields()
        assertEquals(2, fields.size)
        LayoutManager.assignOffsets(fields)

        // 测试默认值（应为 null）
        assertNull(accessor.get(buffer))
        assertEquals(0.0, accessor.getDouble(buffer), 1e-10)

        // 设置和获取值
        accessor.set(buffer, 42.5)
        assertEquals(42.5, accessor.get(buffer) as Double, 1e-10)
        assertEquals(42.5, accessor.getDouble(buffer), 1e-10)

        accessor.setDouble(buffer, -43.5)
        assertEquals(-43.5, accessor.get(buffer) as Double, 1e-10)
        assertEquals(-43.5, accessor.getDouble(buffer), 1e-10)

        // 设置为 null
        accessor.set(buffer, null)
        assertNull(accessor.get(buffer))
        assertEquals(0.0, accessor.getDouble(buffer), 1e-10)

        // 再次设置值
        accessor.set(buffer, 123.25)
        assertEquals(123.25, accessor.get(buffer) as Double, 1e-10)
        assertEquals(123.25, accessor.getDouble(buffer), 1e-10)
    }

    @Test
    fun testDoublePropertyAccessorNonNullableWithNonZeroDefaultValue() {
        val nonZeroDefaultValue = 123.5
        val accessor = DoublePropertyAccessor(nullable = false, defaultValue = nonZeroDefaultValue)
        assertEquals(nonZeroDefaultValue, accessor.defaultValue as Double, 1e-10)

        // 获取字段并分配偏移量
        val fields = accessor.getFields()
        assertEquals(2, fields.size)
        LayoutManager.assignOffsets(fields)

        // 测试默认值
        assertEquals(nonZeroDefaultValue, accessor.get(buffer) as Double, 1e-10)
        assertEquals(nonZeroDefaultValue, accessor.getDouble(buffer), 1e-10)

        // 设置和获取值
        accessor.setDouble(buffer, 456.5)
        assertEquals(456.5, accessor.get(buffer) as Double, 1e-10)
        assertEquals(456.5, accessor.getDouble(buffer), 1e-10)

        accessor.set(buffer, -456.5)
        assertEquals(-456.5, accessor.get(buffer) as Double, 1e-10)
        assertEquals(-456.5, accessor.getDouble(buffer), 1e-10)

        // 重新设置为默认值
        accessor.setDouble(buffer, nonZeroDefaultValue)
        assertEquals(nonZeroDefaultValue, accessor.get(buffer) as Double, 1e-10)
        assertEquals(nonZeroDefaultValue, accessor.getDouble(buffer), 1e-10)

        // 内部存储不太一样，但外部表现为 reset
        accessor.set(buffer, null)
        assertEquals(nonZeroDefaultValue, accessor.get(buffer) as Double, 1e-10)
        assertEquals(nonZeroDefaultValue, accessor.getDouble(buffer), 1e-10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDoublePropertyAccessorNullableCtorError() {
        DoublePropertyAccessor(nullable = true, defaultValue = 123.5)
    }
}