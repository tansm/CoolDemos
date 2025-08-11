package org.example

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class CompactStringUtilsTest {
    private fun enabled(): Boolean = CompactStringUtils.encode("x") !is String

    @Test
    fun ascii_short_uses_String4_and_roundtrips() {
        assumeTrue("CompactString disabled on this runtime; skipping.", enabled())
        val r = CompactStringUtils.encode("abc")
        assertTrue(r is Any && r !is String)
        assertTrue(r!!::class.java.name.contains("String4"))
        assertEquals("abc", r.toString())
    }

    @Test
    fun ascii_30_chars_into_String36() {
        assumeTrue(enabled())
        val s = "x".repeat(30)
        val r = CompactStringUtils.encode(s)
        assertTrue(r is Any && r !is String)
        assertTrue(r!!::class.java.name.contains("String36"))
        assertEquals(s, r.toString())
    }

    @Test
    fun non_ascii_small_into_String12() {
        assumeTrue(enabled())
        val s = "汉字"
        val r = CompactStringUtils.encode(s)
        assertTrue(r is Any && r !is String)
        assertTrue(r!!::class.java.name.contains("String12"))
        assertEquals(s, r.toString())
    }

    @Test
    fun non_ascii_boundary_17_ok_18_fallback() {
        assumeTrue(enabled())
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
        assumeTrue(enabled())
        val s = "a汉b"
        val r = CompactStringUtils.encode(s)
        assertTrue(r is Any && r !is String)
        assertEquals(s, r.toString())
    }

    @Test
    fun empty_and_null() {
        // null → null；空串 → 原始空串对象（不会压缩）
        assertNull(CompactStringUtils.encode(null))
        val e = CompactStringUtils.encode("")
        assertTrue(e === "")
    }

    // --- 新增：ASCII 0..37 sweep（0 为特例：始终返回同一空串实例） ---
    @Test
    fun ascii_length_sweep_0_to_37_with_expected_class_and_roundtrip() {
        assumeTrue(enabled())
        for (len in 0..37) {
            val s = "a".repeat(len)
            val o = CompactStringUtils.encode(s)

            if (len == 0) {
                // 约定：空串永远不压缩，恒等于全局同一实例
                assertTrue(o === "")
                continue
            }

            val total = 1 + len // ASCII 总字节
            val shouldCompress = total <= 36
            if (!shouldCompress) {
                assertTrue("len=$len should fallback", o is String)
                assertEquals(s, o)
                continue
            }

            assertTrue("len=$len should be compact", o is Any && o !is String)
            val clazz = o!!::class.java.name
            val expect = when {
                total <= 4  -> "String4"
                total <= 12 -> "String12"
                total <= 20 -> "String20"
                total <= 28 -> "String28"
                else        -> "String36"
            }
            assertTrue("len=$len expect=$expect but got $clazz", clazz.contains(expect))
            assertEquals("roundtrip failed at len=$len", s, o.toString())
        }
    }

    // --- 新增：UTF-16 0..19 sweep（0 为特例：始终返回同一空串实例） ---
    @Test
    fun utf16_length_sweep_0_to_19_chinese() {
        assumeTrue(enabled())
        for (len in 0..19) {
            val s = "汉".repeat(len)
            val o = CompactStringUtils.encode(s)

            if (len == 0) {
                assertTrue(o === "")
                continue
            }

            val total = 1 + 2*len
            val shouldCompress = total <= 36
            if (!shouldCompress) {
                assertTrue("len=$len should fallback", o is String)
                assertEquals(s, o)
                continue
            }

            assertTrue(o is Any && o !is String)
            val clazz = o!!::class.java.name
            val expect = when {
                total <= 4  -> "String4"
                total <= 12 -> "String12"
                total <= 20 -> "String20"
                total <= 28 -> "String28"
                else        -> "String36"
            }
            assertTrue("utf16 len=$len expect=$expect but got $clazz", clazz.contains(expect))
            assertEquals("utf16 roundtrip failed len=$len", s, o.toString())
        }
    }

    // --- 新增：混合（ASCII + 中文） sweep（总字节判定 + 往返）---
    @Test
    fun mixed_ascii_chinese_sweep() {
        assumeTrue(enabled())
        // 构造若干组合：前缀 ASCII，后缀 1..5 个中文
        for (asciiLen in 0..30 step 3) {
            for (cnLen in 1..5) {
                val s = "a".repeat(asciiLen) + "汉".repeat(cnLen)
                val o = CompactStringUtils.encode(s)
                val total = 1 + 2* (asciiLen + cnLen)
                val shouldCompress = total <= 36
                if (!shouldCompress) {
                    assertTrue(o is String)
                    assertEquals(s, o)
                } else {
                    assertTrue(o is Any && o !is String)
                    assertEquals(s, o.toString())
                }
            }
        }
    }

    @Test
    fun ascii_exact_thresholds_hit_right_class() {
        assumeTrue(enabled())
        val cases = listOf(
            3 to "String4",   // 1+3=4
            11 to "String12", // 1+11=12
            19 to "String20", // 1+19=20
            27 to "String28", // 1+27=28
            35 to "String36"  // 1+35=36
        )
        for ((len, expect) in cases) {
            val s = "a".repeat(len)
            val o = CompactStringUtils.encode(s)
            assertTrue(o is Any && o !is String)
            assertTrue(o!!::class.java.name.contains(expect))
            assertEquals(s, o.toString())
        }
        // 36 → fallback
        val s36 = "a".repeat(36)
        val o36 = CompactStringUtils.encode(s36)
        assertTrue(o36 is String)
        assertEquals(s36, o36)
    }
}
