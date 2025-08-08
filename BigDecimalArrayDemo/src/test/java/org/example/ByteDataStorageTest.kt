package org.example

import org.junit.Assert
import org.junit.Test
import java.lang.reflect.Field
import java.math.BigDecimal

class ByteDataStorageTest {

    /** 辅助方法：获取 objectMap 的实际长度 */
    private fun getObjectMapLength(storage: ByteDataStorage): Int {
        val field: Field = ByteDataStorage::class.java.getDeclaredField("objectMap")
        field.isAccessible = true
        val arr = field.get(storage) as Array<*>
        return arr.size
    }

    @Test
    fun testBasicGetSetLocalValue() {
        val dt = DynamicObjectType()
        val intAccessor = dt.register(IntPropertyAccessor(nullable = false, defaultValue = 42))
        val storage = dt.createInstance()

        // 初始值
        Assert.assertEquals(42, storage.getLocalValue(intAccessor))
        // objectMap 初始长度应为0（无object字段）
        Assert.assertEquals(0, getObjectMapLength(storage))

        // 设置新值
        storage.setLocalValue(intAccessor, 100)
        Assert.assertEquals(100, storage.getLocalValue(intAccessor))
        Assert.assertEquals(0, getObjectMapLength(storage))
    }

    @Test
    fun testObjectMapSetAndGetWithinCapacity() {
        val storage = ByteDataStorage(8, 2, 4)
        Assert.assertEquals(2, getObjectMapLength(storage))
        storage.setObject(0, "hello")
        storage.setObject(1, 123)
        Assert.assertEquals("hello", storage.getObject(0))
        Assert.assertEquals(123, storage.getObject(1))
        Assert.assertNull(storage.getObject(2))
        // 未扩容
        Assert.assertEquals(2, getObjectMapLength(storage))
    }

    @Test
    fun testObjectMapSetNullDoesNotExpand() {
        val storage = ByteDataStorage(8, 1, 2)
        Assert.assertEquals(1, getObjectMapLength(storage))
        storage.setObject(1, null) // 不应扩容
        Assert.assertNull(storage.getObject(1))
        Assert.assertEquals(1, getObjectMapLength(storage))
    }

    @Test
    fun testObjectMapAutoExpandWithinMax() {
        val storage = ByteDataStorage(8, 1, 4)
        Assert.assertEquals(1, getObjectMapLength(storage))
        storage.setObject(2, "abc")
        Assert.assertEquals("abc", storage.getObject(2))
        // 扩容后还能访问原有数据
        storage.setObject(0, "zero")
        Assert.assertEquals("zero", storage.getObject(0))
        // 已扩容到至少3
        Assert.assertTrue(getObjectMapLength(storage) >= 3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testSetObjectNegativeKeyThrows() {
        val storage = ByteDataStorage(8, 1, 2)
        storage.setObject(-1, "bad")
    }

    @Test(expected = IllegalStateException::class)
    fun testSetObjectExceedMaxThrows() {
        val storage = ByteDataStorage(8, 1, 2)
        storage.setObject(3, "bad") // maxObjectSize = 2
    }

    @Test
    fun testGetObjectOutOfRangeReturnsNull() {
        val storage = ByteDataStorage(8, 1, 2)
        Assert.assertNull(storage.getObject(5))
        Assert.assertEquals(1, getObjectMapLength(storage))
    }

    @Test
    fun testMemberClone() {
        val storage = ByteDataStorage(8, 2, 4)
        storage.setObject(0, "a")
        storage.setObject(1, "b")
        val clone = storage.memberClone()
        Assert.assertEquals("a", clone.getObject(0))
        Assert.assertEquals("b", clone.getObject(1))
        // 修改 clone 不影响原对象
        clone.setObject(0, "x")
        Assert.assertEquals("a", storage.getObject(0))
        // objectMap 长度一致
        Assert.assertEquals(getObjectMapLength(storage), getObjectMapLength(clone))
    }

    @Test
    fun testObjectMapExpandToMaxSize() {
        // 初始 objectMap 大小为 3，最大为 4
        val storage = ByteDataStorage(8, 3, 4)
        Assert.assertEquals(3, getObjectMapLength(storage))
        // 填满初始容量
        storage.setObject(0, "a")
        storage.setObject(1, "b")
        storage.setObject(2, "c")
        // 访问第 3 个（下标为 3），应扩容到 maxSize=4，而不是 6
        storage.setObject(3, "d")
        Assert.assertEquals("a", storage.getObject(0))
        Assert.assertEquals("b", storage.getObject(1))
        Assert.assertEquals("c", storage.getObject(2))
        Assert.assertEquals("d", storage.getObject(3))
        // 再次设置 3，不应报错
        storage.setObject(3, "dd")
        Assert.assertEquals("dd", storage.getObject(3))
        // 扩容到最大
        Assert.assertEquals(4, getObjectMapLength(storage))
        // 超过 maxSize 应抛异常
        Assert.assertThrows(IllegalStateException::class.java) {
            storage.setObject(4, "overflow")
        }
    }

    @Test
    fun testCalcObjectInitSizeAndExpansion() {
        // 用 ObjectPropertyAccessor 模拟高概率（requiresObjectStorage=1.0），BigDecimalPropertyAccessor 模拟低概率（0.001）
        val dt = DynamicObjectType()
        repeat(10) { dt.register(BigDecimalPropertyAccessor(nullable = true)) }
        repeat(80) { dt.register(IntPropertyAccessor(false, 0)) }
        repeat(10) { dt.register(ObjectPropertyAccessor()) }
        val storage = dt.createInstance()
        // 预分配应为 10（Object高概率），BigDecimal低概率不计入initSize的整数部分
        Assert.assertEquals(10, getObjectMapLength(storage))

        // 赋值 Object 已经预分配，不会发生扩容，
        val objProperty99 = dt.properties[99] as ObjectPropertyAccessor
        storage.setLocalValue(objProperty99, "string99")
        Assert.assertEquals("string99", storage.getLocalValue(objProperty99))
        Assert.assertEquals(10, getObjectMapLength(storage))

        // 赋值普通的 BigDecimal 不触发扩容，因为直接写到 ByteArray
        val decimalProperty0 = dt.properties[0] as BigDecimalPropertyAccessor
        storage.setLocalValue(decimalProperty0, BigDecimal("99.99"))
        Assert.assertEquals(BigDecimal("99.99"), storage.getLocalValue(decimalProperty0))
        Assert.assertEquals(10, getObjectMapLength(storage))

        // 触发扩容
        val bigValue = BigDecimal.valueOf(Long.MAX_VALUE) + BigDecimal.valueOf(Long.MAX_VALUE)
        storage.setLocalValue(decimalProperty0, bigValue)
        Assert.assertEquals(bigValue, storage.getLocalValue(decimalProperty0))
        Assert.assertTrue(getObjectMapLength(storage) in 11 .. 20)
    }
}