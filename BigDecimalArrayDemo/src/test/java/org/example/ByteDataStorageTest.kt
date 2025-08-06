package org.example

import org.junit.Assert
import org.junit.Test

class ByteDataStorageTest {

    @Test
    fun testBasicGetSetLocalValue() {
        val dt = DynamicObjectType()
        val intAccessor = dt.register(IntPropertyAccessor(nullable = false, defaultValue = 42))
        val storage = dt.createInstance()

        // 初始值
        Assert.assertEquals(42, storage.getLocalValue(intAccessor))

        // 设置新值
        storage.setLocalValue(intAccessor, 100)
        Assert.assertEquals(100, storage.getLocalValue(intAccessor))
    }

    @Test
    fun testObjectMapSetAndGetWithinCapacity() {
        val storage = ByteDataStorage(8, 2, 4)
        storage.setObject(0, "hello")
        storage.setObject(1, 123)
        Assert.assertEquals("hello", storage.getObject(0))
        Assert.assertEquals(123, storage.getObject(1))
        Assert.assertNull(storage.getObject(2))
    }

    @Test
    fun testObjectMapSetNullDoesNotExpand() {
        val storage = ByteDataStorage(8, 1, 2)
        storage.setObject(1, null) // 不应扩容
        Assert.assertNull(storage.getObject(1))
    }

    @Test
    fun testObjectMapAutoExpandWithinMax() {
        val storage = ByteDataStorage(8, 1, 4)
        storage.setObject(2, "abc")
        Assert.assertEquals("abc", storage.getObject(2))
        // 扩容后还能访问原有数据
        storage.setObject(0, "zero")
        Assert.assertEquals("zero", storage.getObject(0))
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
    }

    @Test
    fun testObjectMapExpandToMaxSize() {
        // 初始 objectMap 大小为 3，最大为 4
        val storage = ByteDataStorage(8, 3, 4)
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
        // 超过 maxSize 应抛异常
        Assert.assertThrows(IllegalStateException::class.java) {
            storage.setObject(4, "overflow")
        }
    }
}