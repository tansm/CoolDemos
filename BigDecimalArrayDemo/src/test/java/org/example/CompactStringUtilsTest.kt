package org.example

import org.junit.Assert.*
import org.junit.Test


class CompactStringUtilsTest {
    private fun enabled(): Boolean = CompactStringUtils.encode("x") !is String

    @Test
    fun ascii_short_uses_String4_and_roundtrips() {
        assertTrue("CompactString disabled on this runtime; skipping.",enabled())
        val r = CompactStringUtils.encode("abc")
        assertTrue(r is Any && r !is String)
        assertTrue(r!!::class.java.name.contains("String4"))
        assertEquals("abc", r.toString())
    }

    @Test
    fun ascii_30_chars_into_String36() {
        assertTrue(enabled())
        val s = "x".repeat(30)
        val r = CompactStringUtils.encode(s)
        assertTrue(r is Any && r !is String)
        assertTrue(r!!::class.java.name.contains("String36"))
        assertEquals(s, r.toString())
    }

    @Test
    fun non_ascii_small_into_String12() {
        assertTrue(enabled())
        val s = "汉字"
        val r = CompactStringUtils.encode(s)
        assertTrue(r is Any && r !is String)
        assertTrue(r!!::class.java.name.contains("String12"))
        assertEquals(s, r.toString())
    }

    @Test
    fun non_ascii_boundary_17_ok_18_fallback() {
        assertTrue(enabled())
        val s17 = "汉".repeat(17) // 1+34=35 → String36
        val r17 = CompactStringUtils.encode(s17)
        assertTrue(r17 is Any && r17 !is String)
        assertTrue(r17!!::class.java.name.contains("String36"))
        assertEquals(s17, r17.toString())

        val s18 = "汉".repeat(18) // 1+36=37 → fallback
        val r18 = CompactStringUtils.encode(s18)
        assertTrue(r18 is String)
        assertEquals(s18, r18)
    }

    @Test
    fun mixed_text_roundtrip() {
        assertTrue(enabled())
        val s = "a汉b"
        val r = CompactStringUtils.encode(s)
        assertTrue(r is Any && r !is String)
        assertEquals(s, r.toString())
    }

    @Test
    fun empty_and_null() {
        // null → null；空串 → 原始空串对象
        assertNull(CompactStringUtils.encode(null))
        val e = CompactStringUtils.encode("")
        assertTrue(e === "")
    }
}