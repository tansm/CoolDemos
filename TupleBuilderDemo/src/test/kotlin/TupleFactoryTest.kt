package com.example.orm

import kotlin.test.*

// --- 单元测试 ---
class TupleFactoryTest {

    private lateinit var tupleFactory: TupleFactory

    @BeforeTest
    fun setup() {
        // 对于 Java 源代码编译，我们不需要特殊的 URLClassLoader，因为它会使用 ToolProvider.getSystemJavaCompiler()
        // 并且通过 InMemoryClassLoader 来加载生成的类。
        val tupleBuilder = TupleBuilder("com.mycompany.generated.tuples")
        tupleBuilder.sourceCodeOutStream = System.out
        tupleFactory = TupleFactory(tupleBuilder)
    }

    @Test
    fun testTupleClassCreationAndInstantiation() {
        val tupleType1 = listOf(Int::class.java, Int::class.java, String::class.java)
        val tupleInstance1 = tupleFactory.getOrCreateTuple(tupleType1)
        val tupleClass1 = tupleInstance1.javaClass // 获取实际的类

        // 验证类名符合预期: Tuple_IIO (没有rest字段)
        assertEquals("com.mycompany.generated.tuples.Tuple_IIO", tupleClass1.name)
        // 验证继承了 AbstractTuple
        assertTrue(AbstractTuple::class.java.isAssignableFrom(tupleClass1))

        // 验证字段数量和类型 (getSize() 应该返回原始总数)
        assertEquals(3, tupleInstance1.size)
        // 关键验证：getFieldType 对于 String 应该返回 Object.class
        assertEquals(Int::class.java, tupleInstance1.getFieldType(0))
        assertEquals(Any::class.java, tupleInstance1.getFieldType(2)) // Corrected assertion to expect Object.class

        // 使用 setXXX 方法设置字段值
        tupleInstance1.setInt(0, 10)
        tupleInstance1.setInt(1, 20)
        tupleInstance1.setItem(2, "Hello World")

        // 验证通过 ITuple 接口获取字段值
        assertEquals(10, tupleInstance1.getItem(0))
        assertEquals(20, tupleInstance1.getInt(1))
        assertEquals("Hello World", tupleInstance1.getItem(2))

        // 验证 toString()
        assertEquals("Tuple_IIO(item0=10, item1=20, item2=Hello World)", tupleInstance1.toString())
    }

    @Test
    fun testTupleClassReuse() {
        val tupleType1 = listOf(Int::class.java, Int::class.java, String::class.java)
        val tupleInstance1 = tupleFactory.getOrCreateTuple(tupleType1)
        val tupleClass1 = tupleInstance1.javaClass

        val tupleInstance1Again = tupleFactory.getOrCreateTuple(tupleType1)
        val tupleClass1Again = tupleInstance1Again.javaClass

        // 验证两次获取的是同一个类实例 (由 ClassLoader 缓存)
        assertTrue(tupleClass1 === tupleClass1Again)
    }

    @Test
    fun testDifferentTupleClasses() {
        val tupleType1 = listOf(Int::class.java, Int::class.java, String::class.java)
        val tupleInstance1 = tupleFactory.getOrCreateTuple(tupleType1)
        val tupleClass1 = tupleInstance1.javaClass

        val tupleType2 = listOf(Long::class.java, Any::class.java)
        val tupleInstance2 = tupleFactory.getOrCreateTuple(tupleType2)
        val tupleClass2 = tupleInstance2.javaClass

        // 验证不同类型组合生成不同的类
        assertFalse(tupleClass1 === tupleClass2)
        assertEquals("com.mycompany.generated.tuples.Tuple_LO", tupleClass2.name)

        assertEquals(2, tupleInstance2.size)
        assertEquals(Long::class.java, tupleInstance2.getFieldType(0))
        assertEquals(Any::class.java, tupleInstance2.getFieldType(1)) // Corrected assertion

        // 使用 setXXX 方法设置字段值
        tupleInstance2.setLong(0, 123L)
        tupleInstance2.setItem(1, "Any Object Here")

        assertEquals(123L, tupleInstance2.getLong(0))
        assertEquals("Any Object Here", tupleInstance2.getItem(1))
    }

    @Test
    fun testEmptyTuple() {
        val emptyTupleType = listOf<Class<*>>()
        val emptyInstance = tupleFactory.getOrCreateTuple(emptyTupleType)
        val emptyTupleClass = emptyInstance.javaClass

        assertEquals("com.mycompany.generated.tuples.Tuple_", emptyTupleClass.name)
        assertTrue(AbstractTuple::class.java.isAssignableFrom(emptyTupleClass))

        assertEquals(0, emptyInstance.size)
        assertEquals("Tuple_()", emptyInstance.toString())

        // 验证越界访问
        assertFailsWith<IndexOutOfBoundsException> { emptyInstance.getItem(0) }
        assertFailsWith<IndexOutOfBoundsException> { emptyInstance.getInt(0) } // getInt also handles bounds
        assertFailsWith<IndexOutOfBoundsException> { emptyInstance.getFieldType(-1) }
    }

    @Test
    fun testSingleElementTuple() {
        val singleIntTupleType = listOf(Int::class.java)
        val singleInstance = tupleFactory.getOrCreateTuple(singleIntTupleType)
        val singleIntTupleClass = singleInstance.javaClass

        assertEquals("com.mycompany.generated.tuples.Tuple_I", singleIntTupleClass.name)
        assertTrue(AbstractTuple::class.java.isAssignableFrom(singleIntTupleClass))

        assertEquals(1, singleInstance.size)
        assertEquals(Int::class.java, singleInstance.getFieldType(0))

        // 使用 setXXX 方法设置字段值
        singleInstance.setInt(0, 123)

        assertEquals(123, singleInstance.getItem(0))
        assertEquals(123, singleInstance.getInt(0))
        assertEquals("Tuple_I(item0=123)", singleInstance.toString())
    }

    @Test
    fun testTupleWithEightElements_Chaining() {
        val tupleType = listOf(
            Int::class.java, Int::class.java, Int::class.java, Int::class.java,
            Int::class.java, Int::class.java, Int::class.java, // First 7 direct fields
            String::class.java // 8th element, goes into rest
        )
        val instance = tupleFactory.getOrCreateTuple(tupleType)
        val rootTupleClass = instance.javaClass

        // 根元组类名应该反映其直接字段和 rest 字段的类型缩写
        assertEquals("com.mycompany.generated.tuples.Tuple_IIIIIIIR", rootTupleClass.name) // 7个I + 1个O (代表嵌套的ITuple)
        assertTrue(AbstractTuple::class.java.isAssignableFrom(rootTupleClass))

        assertEquals(8, instance.size) // 总大小是8
        assertEquals(7, instance.directSize)

        // 使用 setXXX 方法设置前7个直接字段
        for (i in 0 until 7) {
            instance.setInt(i, i + 1)
        }

        // 获取并设置 rest 字段
        val restField = rootTupleClass.getDeclaredField("rest").apply { isAccessible = true } // 使用 "rest" 字段名
        val nestedInstance = restField.get(instance) as AbstractTuple // 获取工厂创建并链接的嵌套实例
        val nestedTupleClass = nestedInstance.javaClass
        assertTrue(AbstractTuple::class.java.isAssignableFrom(nestedTupleClass))
        assertEquals("com.mycompany.generated.tuples.Tuple_O", nestedTupleClass.name) // 嵌套元组的类名

        // setItem 设置 String 字段
        nestedInstance.setItem(0, "Nested String Value")

        // 验证通过根元组的 ITuple 接口访问所有字段
        assertEquals(1, instance.getInt(0))
        assertEquals(7, instance.getInt(6))
        assertEquals("Nested String Value", instance.getItem(7))
        // 关键验证：getFieldType 对于 String 应该返回 Object.class
        assertEquals(Any::class.java, instance.getFieldType(7)) // Corrected assertion to expect Object.class

        // 验证 toString() 包含嵌套信息
        assertEquals("Tuple_IIIIIIIR(item0=1, item1=2, item2=3, item3=4, item4=5, item5=6, item6=7, rest=Tuple_O(item0=Nested String Value))", instance.toString())
    }

    @Test
    fun testTupleWithMoreThanEightElements_MultipleChaining() {
        val tupleType = listOf(
            Int::class.java, Int::class.java, Int::class.java, Int::class.java,
            Int::class.java, Int::class.java, Int::class.java, // First 7 direct
            String::class.java, Boolean::class.java, // Next 2 (8th, 9th)
            Long::class.java // 10th
        )
        val instance = tupleFactory.getOrCreateTuple(tupleType)
        val rootTupleClass = instance.javaClass

        // 根元组类名
        assertEquals("com.mycompany.generated.tuples.Tuple_IIIIIIIR", rootTupleClass.name)
        assertTrue(AbstractTuple::class.java.isAssignableFrom(rootTupleClass))

        assertEquals(10, instance.size)
        assertEquals(7, instance.directSize)

        // 使用 setXXX 方法设置前7个字段
        for (i in 0 until 7) {
            instance.setInt(i, i + 10)
        }

        // 获取并设置 rest 字段 (嵌套元组1)
        val restField1 = rootTupleClass.getDeclaredField("rest").apply { isAccessible = true } // 使用 "rest"字段名
        val nestedInstance1 = restField1.get(instance) as AbstractTuple
        val nestedTupleClass1 = nestedInstance1.javaClass
        assertTrue(AbstractTuple::class.java.isAssignableFrom(nestedTupleClass1))
        assertEquals("com.mycompany.generated.tuples.Tuple_OBL", nestedTupleClass1.name) // String, Boolean, Long

        // setItem 设置 String 字段
        nestedInstance1.setItem(0, "String Val")
        nestedInstance1.setBoolean(1, true)
        nestedInstance1.setLong(2, 999L) // 直接设置Long

        // 验证所有字段的值和类型
        assertEquals(10, instance.getInt(0))
        assertEquals(16, instance.getInt(6))
        assertEquals("String Val", instance.getItem(7)) // 8th field (index 7)
        assertEquals(true, instance.getBoolean(8)) // 9th field (index 8)
        assertEquals(999L, instance.getLong(9)) // 10th field (index 9)

        // 关键验证：getFieldType 对于非原始类型应该返回 Object.class
        assertEquals(Any::class.java, instance.getFieldType(7)) // Corrected assertion
        assertEquals(Boolean::class.java, instance.getFieldType(8))
        assertEquals(Long::class.java, instance.getFieldType(9)) // Long is primitive, should remain Long.class

        // 验证 toString()
        val expectedToString = "Tuple_IIIIIIIR(item0=10, item1=11, item2=12, item3=13, item4=14, item5=15, item6=16, rest=Tuple_OBL(item0=String Val, item1=true, item2=999))"
        assertEquals(expectedToString, instance.toString())
    }

    @Test
    fun testAccessOutOfBounds() {
        val tupleType = listOf(Int::class.java, String::class.java)
        val instance = tupleFactory.getOrCreateTuple(tupleType)

        assertFailsWith<IndexOutOfBoundsException> { instance.getItem(-1) }
        assertFailsWith<IndexOutOfBoundsException> { instance.getItem(2) }
        assertFailsWith<IndexOutOfBoundsException> { instance.getInt(2) } // getInt also handles bounds
        assertFailsWith<IndexOutOfBoundsException> { instance.getFieldType(-1) }
    }

    @Test
    fun testTypeMismatchAccessors() {
        val tupleType = listOf(Int::class.java, String::class.java)
        val instance = tupleFactory.getOrCreateTuple(tupleType)
        val tupleClass = instance.javaClass

        tupleClass.getDeclaredField("item0").apply { isAccessible = true }.set(instance, 100)
        tupleClass.getDeclaredField("item1").apply { isAccessible = true }.set(instance, "Test String")

        assertEquals(100, instance.getInt(0))
        assertEquals("Test String", instance.getItem(1))

        // 尝试获取错误类型
        assertFailsWith<ClassCastException> { instance.getLong(0) } // Int is not Long
        assertFailsWith<ClassCastException> { instance.getInt(1) } // String is not Int, getInt now handles this via bytecode
    }

    @Test
    fun testIterator_EmptyTuple() {
        val tupleType = emptyList<Class<*>>()
        val tuple = tupleFactory.getOrCreateTuple(tupleType)
        val iterated = tuple.toList()
        assertTrue(iterated.isEmpty())
    }

    @Test
    fun testIterator_LessThan8Fields() {
        val tupleType = listOf(Int::class.java, String::class.java, Boolean::class.java)
        val tuple = tupleFactory.getOrCreateTuple(tupleType)
        tuple.setInt(0, 42)
        tuple.setItem(1, "abc")
        tuple.setBoolean(2, true)
        val iterated = tuple.toList()
        assertEquals(listOf(42, "abc", true), iterated)
    }

    @Test
    fun testIterator_MoreThan8Fields() {
        val tupleType = List(18) { i ->
            when (i % 3) {
                0 -> Int::class.java
                1 -> String::class.java
                else -> Boolean::class.java
            }
        }
        val tuple = tupleFactory.getOrCreateTuple(tupleType)
        for (i in 0 until 18) {
            when (i % 3) {
                0 -> tuple.setInt(i, i)
                1 -> tuple.setItem(i, "str$i")
                else -> tuple.setBoolean(i, i % 2 == 0)
            }
        }
        val expected = List(18) { i ->
            when (i % 3) {
                0 -> i
                1 -> "str$i"
                else -> i % 2 == 0
            }
        }
        val iterated = tuple.toList()
        assertEquals(expected, iterated)
    }

    @Test
    fun testGeneratedClassFields() {
        val tupleType = listOf(Int::class.java, String::class.java, Boolean::class.java, Long::class.java)
        val tuple = tupleFactory.getOrCreateTuple(tupleType)
        val clazz = tuple.javaClass
        // 检查字段名和类型
        val fields = clazz.declaredFields.associateBy { it.name }
        assertTrue(fields.containsKey("item0"))
        assertTrue(fields.containsKey("item1"))
        assertTrue(fields.containsKey("item2"))
        assertTrue(fields.containsKey("item3"))
        assertEquals(Int::class.javaPrimitiveType, fields["item0"]?.type)
        assertEquals(Any::class.java, fields["item1"]?.type) // String 字段生成 Object
        assertEquals(Boolean::class.javaPrimitiveType, fields["item2"]?.type)
        assertEquals(Long::class.javaPrimitiveType, fields["item3"]?.type)
    }
}