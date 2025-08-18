package org.example

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Date
import java.util.UUID

class DynamicObjectTypeTest {

    @Test
    fun testCreateInstanceNonNullable() {
        val dt = DynamicObjectType().apply {
            register(BooleanPropertyAccessor(false, defaultValue = false))   // 0
            register(BytePropertyAccessor(false, 0))            // 1
            register(ShortPropertyAccessor(false, 0))           // 2
            register(IntPropertyAccessor(false, 0))             // 3
            register(LongPropertyAccessor(false, 0L))           // 4
            register(FloatPropertyAccessor(false, 0f))          // 5
            register(DoublePropertyAccessor(false, 0.0))        // 6
            register(BigDecimalPropertyAccessor(false))                      // 7
            register(UUIDPropertyAccessor(false))                            // 8
            register(LocalDatePropertyAccessor(false))                       // 9
            register(LocalTimePropertyAccessor(false))                       // 10
            register(LocalDateTimePropertyAccessor(false))                   // 11
            register(InstantPropertyAccessor(false))                         // 12
            register(DatePropertyAccessor(false))                            // 13
        }

        val target = dt.createInstance()
        assertEquals(false, dt.properties[0].get(target) )
        assertEquals(0.toByte(), dt.properties[1].get(target))
        assertEquals(0.toShort(), dt.properties[2].get(target))
        assertEquals(0, dt.properties[3].get(target))
        assertEquals(0L, dt.properties[4].get(target))
        assertEquals(0.0f, dt.properties[5].get(target))
        assertEquals(0.0, dt.properties[6].get(target))
        assertEquals(BigDecimal.ZERO, dt.properties[7].get(target))
        assertEquals(UUID(0L,0L), dt.properties[8].get(target))
        assertEquals(LocalDate.ofEpochDay(0), dt.properties[9].get(target))
        assertEquals(LocalTime.ofNanoOfDay(0), dt.properties[10].get(target))
        assertEquals(LocalDateTime.ofEpochSecond(0,0, ZoneOffset.UTC), dt.properties[11].get(target))
        assertEquals(Instant.EPOCH, dt.properties[12].get(target))
        assertEquals(Date(0), dt.properties[13].get(target))

        testOtherValues(dt, target)
    }

    @Test
    fun testCreateInstanceNullable() {
        val dt = DynamicObjectType().apply {
            register(BooleanPropertyAccessor(true, defaultValue = false))   // 0
            register(BytePropertyAccessor(true, 0))            // 1
            register(ShortPropertyAccessor(true, 0))           // 2
            register(IntPropertyAccessor(true, 0))             // 3
            register(LongPropertyAccessor(true, 0L))           // 4
            register(FloatPropertyAccessor(true, 0f))          // 5
            register(DoublePropertyAccessor(true, 0.0))        // 6
            register(BigDecimalPropertyAccessor(true))                      // 7
            register(UUIDPropertyAccessor(true))                            // 8
            register(LocalDatePropertyAccessor(true))                       // 9
            register(LocalTimePropertyAccessor(true))                       // 10
            register(LocalDateTimePropertyAccessor(true))                   // 11
            register(InstantPropertyAccessor(true))                         // 12
            register(DatePropertyAccessor(true))                            // 13
        }

        val target = dt.createInstance()
        assertNull(dt.properties[0].get(target))
        assertNull(dt.properties[1].get(target))
        assertNull(dt.properties[2].get(target))
        assertNull(dt.properties[3].get(target))
        assertNull(dt.properties[4].get(target))
        assertNull(dt.properties[5].get(target))
        assertNull(dt.properties[6].get(target))
        assertNull(dt.properties[7].get(target))
        assertNull(dt.properties[8].get(target))
        assertNull(dt.properties[9].get(target))
        assertNull(dt.properties[10].get(target))
        assertNull(dt.properties[11].get(target))
        assertNull(dt.properties[12].get(target))
        assertNull(dt.properties[13].get(target))

        testOtherValues(dt, target)
    }

    private fun testOtherValues(dt: DynamicObjectType, target: ByteDataStorage) {
        dt.properties[0].set(target, true)
        assertEquals(true, dt.properties[0].get(target))
        dt.properties[1].set(target, 1.toByte())
        assertEquals(1.toByte(), dt.properties[1].get(target))
        dt.properties[2].set(target, 2.toShort())
        assertEquals(2.toShort(), dt.properties[2].get(target))
        dt.properties[3].set(target, 3)
        assertEquals(3, dt.properties[3].get(target))
        dt.properties[4].set(target, 4L)
        assertEquals(4L, dt.properties[4].get(target))
        dt.properties[5].set(target, 5.1f)
        assertEquals(5.1f, dt.properties[5].get(target))
        dt.properties[6].set(target, 6.1)
        assertEquals(6.1, dt.properties[6].get(target))

        dt.properties[7].set(target, BigDecimal("7.1"))
        assertEquals(BigDecimal("7.1"), dt.properties[7].get(target))
        dt.properties[8].set(target, UUID(8L, 8L))
        assertEquals(UUID(8L, 8L), dt.properties[8].get(target))
        dt.properties[9].set(target, LocalDate.ofEpochDay(9))
        assertEquals(LocalDate.ofEpochDay(9), dt.properties[9].get(target))
        dt.properties[10].set(target, LocalTime.ofNanoOfDay(10))
        assertEquals(LocalTime.ofNanoOfDay(10), dt.properties[10].get(target))
        dt.properties[11].set(target, LocalDateTime.ofEpochSecond(11,11, ZoneOffset.UTC))
        assertEquals(LocalDateTime.ofEpochSecond(11,11, ZoneOffset.UTC), dt.properties[11].get(target))
        dt.properties[12].set(target, Instant.ofEpochSecond(12,12))
        assertEquals(Instant.ofEpochSecond(12,12), dt.properties[12].get(target))
        dt.properties[13].set(target, Date(13))
        assertEquals(Date(13), dt.properties[13].get(target))
    }

    @Test
    fun booleanLayoutTest(){
        // boolean 夹杂 Byte，都是对齐一个字节，我们希望 Boolean 可以复用之前的字节。
        val dt = DynamicObjectType().apply {
            register(BooleanPropertyAccessor(false, defaultValue = false))   // 0
            register(BytePropertyAccessor(false, 0))            // 1
            register(BooleanPropertyAccessor(false, defaultValue = false))   // 2
        }
        val target = dt.createInstance()
        assertEquals(2, target.buffer.size)
    }

    @Test
    fun booleanLayout2Test(){
        // 8 个 Boolean 占满一个 Byte。
        val dt = DynamicObjectType().apply {
            register(BooleanPropertyAccessor(false, defaultValue = false))   // 0
            register(BytePropertyAccessor(false, 0))            // 1
            register(BooleanPropertyAccessor(false, defaultValue = false))
            register(BooleanPropertyAccessor(false, defaultValue = false))
            register(BooleanPropertyAccessor(false, defaultValue = false))
            register(BooleanPropertyAccessor(false, defaultValue = false))
            register(BooleanPropertyAccessor(false, defaultValue = false))
            register(BooleanPropertyAccessor(false, defaultValue = false))
            register(BooleanPropertyAccessor(false, defaultValue = false))

            register(BooleanPropertyAccessor(false, defaultValue = false))
            register(BooleanPropertyAccessor(false, defaultValue = false))
            register(BooleanPropertyAccessor(false, defaultValue = false))
            register(BooleanPropertyAccessor(false, defaultValue = false))
            register(BooleanPropertyAccessor(false, defaultValue = false))
            register(BooleanPropertyAccessor(false, defaultValue = false))
            register(BooleanPropertyAccessor(false, defaultValue = false))
            register(BooleanPropertyAccessor(false, defaultValue = false))
        }
        val target = dt.createInstance()
        assertEquals(3, target.buffer.size)
    }

    @Test
    fun booleanLayout3Test(){
        // 没有 boolean 时，不要错误的计算字节数。
        val dt = DynamicObjectType().apply {
            register(BytePropertyAccessor(false, 0))
            register(BytePropertyAccessor(false, 0))
        }
        val target = dt.createInstance()
        assertEquals(2, target.buffer.size)
    }
}