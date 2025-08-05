package org.example

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.*

class PropertyAccessorTest {

    private lateinit var buffer: ByteArray

    @Before
    fun setUp() {
        // 创建一个足够大的 ByteBuffer 用于测试
        buffer = ByteArray(64)
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

    @Test
    fun testBigDecimalPropertyAccessorNullable() {
        val dt = DynamicObjectType()
        val accessor = dt.register(BigDecimalPropertyAccessor(nullable = true))
        val storage = dt.createInstance()

        // 默认值
        assertNull(accessor.defaultValue)
        assertNull(accessor.get(storage))

        // 设置 null
        accessor.set(storage, null)
        assertNull(accessor.get(storage))

        // 设置小数值
        val small = BigDecimal("123.45")
        accessor.set(storage, small)
        assertEquals(small, accessor.get(storage))

        // 设置大数值（超出 long）
        val big = BigDecimal("123456789012345678901234567890.123")
        accessor.set(storage, big)
        assertEquals(big, accessor.get(storage))

        // 设置 scale 超出 byte 范围
        val largeScale = BigDecimal.valueOf(123, 128)
        accessor.set(storage, largeScale)
        assertEquals(largeScale, accessor.get(storage))

        val minLong = BigDecimal.valueOf(Long.MIN_VALUE, 0)
        accessor.set(storage, minLong)
        assertEquals(minLong, accessor.getBigDecimal(storage))

        // 再次设置 null
        accessor.set(storage, null)
        assertNull(accessor.get(storage))
    }

    @Test
    fun testBigDecimalPropertyAccessorNonNullable() {
        val dt = DynamicObjectType()
        val accessor = dt.register(BigDecimalPropertyAccessor(nullable = false))
        val storage = dt.createInstance()

        // 默认值
        assertEquals(BigDecimal.ZERO, accessor.defaultValue)
        assertEquals(BigDecimal.ZERO, accessor.get(storage))

        // 设置 null，等价于 reset
        accessor.set(storage, null)
        assertEquals(BigDecimal.ZERO, accessor.get(storage))

        // 设置小数值
        val small = BigDecimal("123.45")
        accessor.set(storage, small)
        assertEquals(small, accessor.get(storage))

        // 设置大数值（超出 long）
        val big = BigDecimal("123456789012345678901234567890.123")
        accessor.set(storage, big)
        assertEquals(big, accessor.get(storage))

        // 设置 scale 超出 byte 范围
        val largeScale = BigDecimal.valueOf(123, 128)
        accessor.set(storage, largeScale)
        assertEquals(largeScale, accessor.get(storage))
    }

    
    @Test
    fun testUUIDPropertyAccessorNullable() {
        val dt = DynamicObjectType()
        val accessor = dt.register(UUIDPropertyAccessor(nullable = true))
        val buffer = dt.createInstance().buffer

        // 默认值
        assertNull(accessor.defaultValue)
        assertNull(accessor.getUUID(buffer))
        assertNull(accessor.get(buffer))

        // 设置 null
        accessor.setUUID(buffer, null)
        assertNull(accessor.getUUID(buffer))
        assertNull(accessor.get(buffer))

        // 设置普通 UUID
        val uuid = java.util.UUID.randomUUID()
        accessor.setUUID(buffer, uuid)
        assertEquals(uuid, accessor.getUUID(buffer))
        assertEquals(uuid, accessor.get(buffer))

        // 设置全 0 UUID
        val zeroUUID = java.util.UUID(0L, 0L)
        accessor.set(buffer, zeroUUID)
        // nullable 时全 0 UUID 也应返回 zeroUUID
        assertEquals(zeroUUID, accessor.getUUID(buffer))
        assertEquals(zeroUUID, accessor.get(buffer))
        assertSame(accessor.getUUID(buffer), accessor.get(buffer))

        val u2 = java.util.UUID(0L, 1L)
        accessor.setUUID(buffer, u2)
        assertEquals(u2, accessor.getUUID(buffer))
        assertEquals(u2, accessor.get(buffer))

        // 再次设置 null
        accessor.setUUID(buffer, null)
        assertNull(accessor.getUUID(buffer))
        assertNull(accessor.get(buffer))
    }

    @Test
    fun testUUIDPropertyAccessorNonNullable() {
        val dt = DynamicObjectType()
        val accessor = dt.register(UUIDPropertyAccessor(nullable = false))
        val buffer = dt.createInstance().buffer

        // 默认值
        val zeroUUID = java.util.UUID(0L, 0L)
        assertEquals(zeroUUID, accessor.defaultValue)
        assertEquals(zeroUUID, accessor.getUUID(buffer))
        assertEquals(zeroUUID, accessor.get(buffer))

        // 设置 null，等价于 reset
        accessor.setUUID(buffer, null)
        assertEquals(zeroUUID, accessor.getUUID(buffer))
        assertEquals(zeroUUID, accessor.get(buffer))

        // 设置普通 UUID
        val uuid = java.util.UUID.randomUUID()
        accessor.setUUID(buffer, uuid)
        assertEquals(uuid, accessor.getUUID(buffer))
        assertEquals(uuid, accessor.get(buffer))

        // 设置全 0 UUID
        accessor.setUUID(buffer, zeroUUID)
        assertEquals(zeroUUID, accessor.getUUID(buffer))
        assertEquals(zeroUUID, accessor.get(buffer))
        assertSame(accessor.getUUID(buffer), accessor.get(buffer))
    }

    @Test
    fun testLocalDatePropertyAccessorNullable() {
        val dt = DynamicObjectType()
        val accessor = dt.register(LocalDatePropertyAccessor(nullable = true))
        val buffer = dt.createInstance().buffer

        // 默认值
        assertNull(accessor.defaultValue)
        assertNull(accessor.getLocalDate(buffer))
        assertNull(accessor.get(buffer))

        // 设置 null
        accessor.setLocalDate(buffer, null)
        assertNull(accessor.getLocalDate(buffer))
        assertNull(accessor.get(buffer))

        // 设置普通日期
        val date = LocalDate.of(2023, 8, 4)
        accessor.setLocalDate(buffer, date)
        assertEquals(date, accessor.getLocalDate(buffer))
        assertEquals(date, accessor.get(buffer))

        // 设置 EMPTY
        accessor.set(buffer, LocalDatePropertyAccessor.EMPTY)
        assertEquals(LocalDatePropertyAccessor.EMPTY, accessor.getLocalDate(buffer))
        assertEquals(LocalDatePropertyAccessor.EMPTY, accessor.get(buffer))

        // 再次设置 null
        accessor.setLocalDate(buffer, null)
        assertNull(accessor.getLocalDate(buffer))
        assertNull(accessor.get(buffer))
    }

    @Test
    fun testLocalDatePropertyAccessorNonNullable() {
        val dt = DynamicObjectType()
        val accessor = dt.register(LocalDatePropertyAccessor(nullable = false))
        val buffer = dt.createInstance().buffer

        // 默认值
        assertEquals(LocalDatePropertyAccessor.EMPTY, accessor.defaultValue)
        assertEquals(LocalDatePropertyAccessor.EMPTY, accessor.getLocalDate(buffer))
        assertEquals(LocalDatePropertyAccessor.EMPTY, accessor.get(buffer))

        // 设置 null，等价于 reset
        accessor.setLocalDate(buffer, null)
        assertEquals(LocalDatePropertyAccessor.EMPTY, accessor.getLocalDate(buffer))
        assertEquals(LocalDatePropertyAccessor.EMPTY, accessor.get(buffer))

        // 设置普通日期
        val date = LocalDate.of(2023, 8, 4)
        accessor.setLocalDate(buffer, date)
        assertEquals(date, accessor.getLocalDate(buffer))
        assertEquals(date, accessor.get(buffer))

        // 设置 EMPTY
        accessor.setLocalDate(buffer, LocalDatePropertyAccessor.EMPTY)
        assertEquals(LocalDatePropertyAccessor.EMPTY, accessor.getLocalDate(buffer))
        assertEquals(LocalDatePropertyAccessor.EMPTY, accessor.get(buffer))
    }

    @Test
    fun testLocalTimePropertyAccessorNullable() {
        val dt = DynamicObjectType()
        val accessor = dt.register(LocalTimePropertyAccessor(nullable = true))
        val buffer = dt.createInstance().buffer

        // 默认值
        assertNull(accessor.defaultValue)
        assertNull(accessor.getLocalTime(buffer))
        assertNull(accessor.get(buffer))

        // 设置 null
        accessor.setLocalTime(buffer, null)
        assertNull(accessor.getLocalTime(buffer))
        assertNull(accessor.get(buffer))

        // 设置普通时间
        val time = LocalTime.of(12, 34, 56, 789000000)
        accessor.setLocalTime(buffer, time)
        assertEquals(time, accessor.getLocalTime(buffer))
        assertEquals(time, accessor.get(buffer))

        // 设置 EMPTY
        accessor.set(buffer, LocalTimePropertyAccessor.EMPTY)
        assertEquals(LocalTimePropertyAccessor.EMPTY, accessor.getLocalTime(buffer))
        assertEquals(LocalTimePropertyAccessor.EMPTY, accessor.get(buffer))

        // 再次设置 null
        accessor.setLocalTime(buffer, null)
        assertNull(accessor.getLocalTime(buffer))
        assertNull(accessor.get(buffer))
    }

    @Test
    fun testLocalTimePropertyAccessorNonNullable() {
        val dt = DynamicObjectType()
        val accessor = dt.register(LocalTimePropertyAccessor(nullable = false))
        val buffer = dt.createInstance().buffer

        // 默认值
        assertEquals(LocalTimePropertyAccessor.EMPTY, accessor.defaultValue)
        assertEquals(LocalTimePropertyAccessor.EMPTY, accessor.getLocalTime(buffer))
        assertEquals(LocalTimePropertyAccessor.EMPTY, accessor.get(buffer))

        // 设置 null，等价于 reset
        accessor.setLocalTime(buffer, null)
        assertEquals(LocalTimePropertyAccessor.EMPTY, accessor.getLocalTime(buffer))
        assertEquals(LocalTimePropertyAccessor.EMPTY, accessor.get(buffer))

        // 设置普通时间
        val time = LocalTime.of(12, 34, 56, 789000000)
        accessor.setLocalTime(buffer, time)
        assertEquals(time, accessor.getLocalTime(buffer))
        assertEquals(time, accessor.get(buffer))

        // 设置 EMPTY
        accessor.setLocalTime(buffer, LocalTimePropertyAccessor.EMPTY)
        assertEquals(LocalTimePropertyAccessor.EMPTY, accessor.getLocalTime(buffer))
        assertEquals(LocalTimePropertyAccessor.EMPTY, accessor.get(buffer))
    }

    @Test
    fun testLocalDateTimePropertyAccessorNullable() {
        val dt = DynamicObjectType()
        val accessor = dt.register(LocalDateTimePropertyAccessor(nullable = true))
        val buffer = dt.createInstance().buffer

        // 默认值
        assertNull(accessor.defaultValue)
        assertNull(accessor.getLocalDateTime(buffer))
        assertNull(accessor.get(buffer))

        // 设置 null
        accessor.setLocalDateTime(buffer, null)
        assertNull(accessor.getLocalDateTime(buffer))
        assertNull(accessor.get(buffer))

        // 设置普通日期时间
        val dtValue = LocalDateTime.of(2023, 8, 4, 12, 34, 56, 789000000)
        accessor.setLocalDateTime(buffer, dtValue)
        assertEquals(dtValue, accessor.getLocalDateTime(buffer))
        assertEquals(dtValue, accessor.get(buffer))

        // 设置 EMPTY
        accessor.set(buffer, LocalDateTimePropertyAccessor.EMPTY)
        assertEquals(LocalDateTimePropertyAccessor.EMPTY, accessor.getLocalDateTime(buffer))
        assertEquals(LocalDateTimePropertyAccessor.EMPTY, accessor.get(buffer))

        val dtValue2 = LocalDateTime.ofEpochSecond(0L, 1, ZoneOffset.UTC)
        accessor.set(buffer, dtValue2)
        assertEquals(dtValue2, accessor.getLocalDateTime(buffer))
        assertEquals(dtValue2, accessor.get(buffer))

        // 再次设置 null
        accessor.setLocalDateTime(buffer, null)
        assertNull(accessor.getLocalDateTime(buffer))
        assertNull(accessor.get(buffer))
    }

    @Test
    fun testLocalDateTimePropertyAccessorNonNullable() {
        val dt = DynamicObjectType()
        val accessor = dt.register(LocalDateTimePropertyAccessor(nullable = false))
        val buffer = dt.createInstance().buffer

        // 默认值
        assertEquals(LocalDateTimePropertyAccessor.EMPTY, accessor.defaultValue)
        assertEquals(LocalDateTimePropertyAccessor.EMPTY, accessor.getLocalDateTime(buffer))
        assertEquals(LocalDateTimePropertyAccessor.EMPTY, accessor.get(buffer))

        // 设置 null，等价于 reset
        accessor.setLocalDateTime(buffer, null)
        assertEquals(LocalDateTimePropertyAccessor.EMPTY, accessor.getLocalDateTime(buffer))
        assertEquals(LocalDateTimePropertyAccessor.EMPTY, accessor.get(buffer))

        // 设置普通日期时间
        val dtValue = LocalDateTime.of(2023, 8, 4, 12, 34, 56, 789000000)
        accessor.setLocalDateTime(buffer, dtValue)
        assertEquals(dtValue, accessor.getLocalDateTime(buffer))
        assertEquals(dtValue, accessor.get(buffer))

        // 设置 EMPTY
        accessor.setLocalDateTime(buffer, LocalDateTimePropertyAccessor.EMPTY)
        assertEquals(LocalDateTimePropertyAccessor.EMPTY, accessor.getLocalDateTime(buffer))
        assertEquals(LocalDateTimePropertyAccessor.EMPTY, accessor.get(buffer))
    }

    @Test
    fun testInstantPropertyAccessorNullable() {
        val dt = DynamicObjectType()
        val accessor = dt.register(InstantPropertyAccessor(nullable = true))
        val buffer = dt.createInstance().buffer

        // 默认值
        assertNull(accessor.defaultValue)
        assertNull(accessor.getInstant(buffer))
        assertNull(accessor.get(buffer))

        // 设置 null
        accessor.setInstant(buffer, null)
        assertNull(accessor.getInstant(buffer))
        assertNull(accessor.get(buffer))

        // 设置普通 Instant
        val instant = Instant.parse("2023-08-04T12:34:56.789Z")
        accessor.setInstant(buffer, instant)
        assertEquals(instant, accessor.getInstant(buffer))
        assertEquals(instant, accessor.get(buffer))

        // 设置 EPOCH
        accessor.set(buffer, Instant.EPOCH)
        assertEquals(Instant.EPOCH, accessor.getInstant(buffer))
        assertEquals(Instant.EPOCH, accessor.get(buffer))

        // 再次设置 null
        accessor.setInstant(buffer, null)
        assertNull(accessor.getInstant(buffer))
        assertNull(accessor.get(buffer))
    }

    @Test
    fun testInstantPropertyAccessorNonNullable() {
        val dt = DynamicObjectType()
        val accessor = dt.register(InstantPropertyAccessor(nullable = false))
        val buffer = dt.createInstance().buffer

        // 默认值
        assertEquals(Instant.EPOCH, accessor.defaultValue)
        assertEquals(Instant.EPOCH, accessor.getInstant(buffer))
        assertEquals(Instant.EPOCH, accessor.get(buffer))

        // 设置 null，等价于 reset
        accessor.setInstant(buffer, null)
        assertEquals(Instant.EPOCH, accessor.getInstant(buffer))
        assertEquals(Instant.EPOCH, accessor.get(buffer))

        // 设置普通 Instant
        val instant = Instant.parse("2023-08-04T12:34:56.789Z")
        accessor.setInstant(buffer, instant)
        assertEquals(instant, accessor.getInstant(buffer))
        assertEquals(instant, accessor.get(buffer))

        // 设置 EPOCH
        accessor.setInstant(buffer, Instant.EPOCH)
        assertEquals(Instant.EPOCH, accessor.getInstant(buffer))
        assertEquals(Instant.EPOCH, accessor.get(buffer))
    }
}