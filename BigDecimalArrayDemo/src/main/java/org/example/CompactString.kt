package org.example

import sun.misc.Unsafe
import java.nio.ByteOrder

object CompactStringUtils {
    private abstract class CompactString {
        // ——— 只有一个字段，用作 header（1 byte）+ padding（3 bytes）, 必须保证是小端布局。
        // 小端存储时，值： 0x01020304 , 在实际的内存布局是： 0x04, 0x03, 0x03, 0x01 ， 也就是说要获取实际的第 0 个字节，是取最低位。
        @Suppress("unused")
        private var header: Int = 0

        protected abstract val compactStringAccessor: CompactStringAccessor?

        override fun toString(): String {
            val accessor = compactStringAccessor ?: error("CompactString accessor not available on this runtime")
            val meta = header
            val isAscii = (meta and 0x80) != 0  // 最高位存储 1， 表示 ascii 模式
            val length = meta and 0x7F          // 长度，理论范围： 0 .. 127
            return if (isAscii) {
                String(accessor.getRawAsciiBytes(this, length), 0, length, Charsets.US_ASCII)
            } else {
                String(accessor.getRawChars(this, length))
            }
        }
    }

    // —— 子类：整块窗口总大小（header(4) + 后续 long*8）——
    private class String4 : CompactString() {
        override val compactStringAccessor: CompactStringAccessor? get() = string4Accessor
    }

    private class String12 : CompactString() {
        @Suppress("unused")
        private var l0: Long = 0L
        override val compactStringAccessor: CompactStringAccessor? get() = string12Accessor
    }

    private class String20 : CompactString() {
        @Suppress("unused")
        private var l0: Long = 0L

        @Suppress("unused")
        private var l1: Long = 0L

        override val compactStringAccessor: CompactStringAccessor? get() = string20Accessor
    }

    private class String28 : CompactString() {
        @Suppress("unused")
        private var l0: Long = 0L

        @Suppress("unused")
        private var l1: Long = 0L

        @Suppress("unused")
        private var l2: Long = 0L

        override val compactStringAccessor: CompactStringAccessor? get() = string28Accessor
    }

    private class String36 : CompactString() {
        @Suppress("unused")
        private var l0: Long = 0L

        @Suppress("unused")
        private var l1: Long = 0L

        @Suppress("unused")
        private var l2: Long = 0L

        @Suppress("unused")
        private var l3: Long = 0L

        override val compactStringAccessor: CompactStringAccessor? get() = string36Accessor
    }

    // ——— 访问器定义：与 CompactString 的某个派生类型一一对应。  ———
    private class CompactStringAccessor(clazz: Class<*>, val maxByteLength: Int) {
        private val _payloadOffset: Long    // 缓存起来

        init {
            // payload 起始偏移 = header 偏移 + 1 字节（复用 header 剩余 3 字节）
            val headerField = clazz.getDeclaredField(HEADER_NAME)
            _payloadOffset = UNSAFE.objectFieldOffset(headerField) + 1
        }

        fun getRawAsciiBytes(obj: CompactString, length: Int): ByteArray {
            val dst = ByteArray(length)
            UNSAFE.copyMemory(
                obj, _payloadOffset, dst,
                BYTE_ARRAY_BASE_OFFSET, length.toLong()
            )
            return dst
        }

        fun getRawChars(obj: CompactString, length: Int): CharArray {
            val dst = CharArray(length)
            UNSAFE.copyMemory(
                obj, _payloadOffset, dst,
                CHAR_ARRAY_BASE_OFFSET, (length * 2).toLong()
            )
            return dst
        }
    }

    // ——— 访问器定义：与 String 对应，如果不兼容 JDK 1.8, 不会实例化  ———
    private class StringAccessor(
        private val valueField: java.lang.reflect.Field
    ) {

    }

    private fun createCompactStringAccessor(clazz: Class<*>, maxByteLength: Int) : CompactStringAccessor?{
        // 如果是不兼容的 String ，不要初始化此实例。accessor 需要先初始化。
        if (accessor == null) return null

        return try {
            CompactStringAccessor(clazz, maxByteLength)
        }catch (_ : Throwable){
            null
        }
    }

        // ===== Unsafe & 常量 =====
        private val UNSAFE: Unsafe = run {
            val f = Unsafe::class.java.getDeclaredField("theUnsafe")
            f.isAccessible = true
            f.get(null) as Unsafe
        }
    private val BYTE_ARRAY_BASE_OFFSET: Long = Unsafe.ARRAY_BYTE_BASE_OFFSET.toLong()
    private val CHAR_ARRAY_BASE_OFFSET: Long = Unsafe.ARRAY_CHAR_BASE_OFFSET.toLong()
    private const val MAX_CHAR_LENGTH = 127
        private const val HEADER_NAME = "header"

        // 运行时访问器（init 中完成；失败则为 null）, 必须放在前面。
        @JvmStatic
        private val accessor: StringAccessor?

        @JvmStatic
        private val string4Accessor = createCompactStringAccessor(String4::class.java, 4)

        @JvmStatic
        private val string12Accessor= createCompactStringAccessor(String12::class.java, 12)

        @JvmStatic
        private val string20Accessor= createCompactStringAccessor(String20::class.java, 20)

        @JvmStatic
        private val string28Accessor= createCompactStringAccessor(String28::class.java, 28)

        @JvmStatic
        private val string36Accessor= createCompactStringAccessor(String36::class.java, 36)

    // todo 下面没有完成
        // 兼容 JDK8 的 String 反射字段（若不可用则为 null）
        @JvmStatic
        private var STRING_VALUE_F: java.lang.reflect.Field? = null

        @JvmStatic
        private var STRING_OFFSET_F: java.lang.reflect.Field? = null

        @JvmStatic
        private var STRING_COUNT_F: java.lang.reflect.Field? = null

        @JvmStatic
        private var STRING_VALUE_OFF: Long = -1


        fun encode(str: String): Any {
            // 上限：最坏情况（UTF-16）总字节 = 1(meta) + 2*len
            val worst = 1 + str.length * 2
            if (worst > 124 || str.length > MAX_CHAR_LENGTH) return str

            // 判 ASCII（短路退出）
            var ascii = true
            for (c in str) {
                if (c.code > 0x7F) {
                    ascii = false; break
                }
            }

            val totalBytes = if (ascii) 1 + str.length else 1 + str.length * 2
            val result: CompactString = when {
                totalBytes <= 4 -> String4()
                totalBytes <= 12 -> String12()
                totalBytes <= 20 -> String20()
                else -> String124()
            }

            // meta：bit7=ASCII 标志，低 7 位 = length
            val meta = ((if (ascii) 0x80 else 0x00) or str.length) and 0xFF
            UNSAFE.putInt(result, headerOff(result.javaClass), meta)

            val po = payloadOff(result.javaClass)
            if (ascii) {
                // 逐字节收集到临时 byte[] 再一次拷贝（char->byte 无法直接 copyMemory）
                val bytes = ByteArray(str.length)
                for (i in str.indices) bytes[i] = (str[i].code and 0x7F).toByte()
                UNSAFE.copyMemory(bytes, BYTE_ARRAY_BASE_OFFSET, result, po, str.length.toLong())
            } else {
                // 非 ASCII：尽量零拷贝从 String 内部 char[] 复制；若不可用则 toCharArray()
                val vf = STRING_VALUE_F
                if (vf != null && STRING_VALUE_OFF >= 0) {
                    val chars = UNSAFE.getObject(str, STRING_VALUE_OFF) as CharArray
                    val len = (STRING_COUNT_F?.getInt(str)) ?: chars.size
                    val start = (STRING_OFFSET_F?.getInt(str)) ?: 0
                    UNSAFE.copyMemory(chars, CHAR_ARRAY_BASE_OFFSET + start * 2L, result, po, (len * 2).toLong())
                } else {
                    val ca = str.toCharArray()
                    UNSAFE.copyMemory(ca, CHAR_ARRAY_BASE_OFFSET, result, po, (ca.size * 2).toLong())
                }
            }
            return result
        }


        // ====== 版式/布局/平台校验 ======
        private fun isLittleEndian(): Boolean = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN

        /** 校验字段顺序/类型/偏移严格递增，且首字段是最小偏移。*/
        fun verifyLayout(clazz: Class<*>, vararg expected: Pair<String, Class<*>>): Boolean {
            return try {
                if (expected.isEmpty()) return false
                val inst = clazz.getDeclaredConstructor().newInstance()
                val nonStatic = clazz.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
                val min = nonStatic.minOf { UNSAFE.objectFieldOffset(it) }
                var last = Long.MIN_VALUE
                for ((idx, e) in expected.withIndex()) {
                    val (name, type) = e
                    val f = clazz.getDeclaredField(name)
                    if (f.type != type) return false
                    val off = UNSAFE.objectFieldOffset(f)
                    if (idx == 0 && off != min) return false
                    if (idx > 0 && off <= last) return false
                    last = off
                }
                // 额外：探针整体拷贝一遍，确保窗口可读
                val size = (inst as CompactString).maxByteLength().toLong()
                val probe = ByteArray(size.toInt())
                UNSAFE.copyMemory(inst, headerOff(clazz), probe, BYTE_ARRAY_BASE_OFFSET, size)
                true
            } catch (_: Throwable) {
                false
            }
        }

        init {
            // 1) 解析 String 内部字段（JDK8 兼容 offset/count；JDK9+ 可能无）
            try {
                val vf = String::class.java.getDeclaredField("value").apply { isAccessible = true }
                STRING_VALUE_F = vf
                STRING_VALUE_OFF = UNSAFE.objectFieldOffset(vf)
            } catch (_: Throwable) {
                STRING_VALUE_F = null
                STRING_VALUE_OFF = -1
            }
            try {
                STRING_OFFSET_F = String::class.java.getDeclaredField("offset").apply { isAccessible = true }
            } catch (_: Throwable) {
                STRING_OFFSET_F = null
            }
            try {
                STRING_COUNT_F = String::class.java.getDeclaredField("count").apply { isAccessible = true }
            } catch (_: Throwable) {
                STRING_COUNT_F = null
            }

            // 2) 平台与布局校验（包含大小端检查）
            val platformOk = isLittleEndian()
            val layoutOk = try {
                verifyLayout(String4::class.java, HEADER_NAME to Int::class.java) &&
                        verifyLayout(
                            String12::class.java,
                            HEADER_NAME to Int::class.java,
                            "l0" to Long::class.java
                        ) &&
                        verifyLayout(
                            String20::class.java,
                            HEADER_NAME to Int::class.java,
                            "l0" to Long::class.java,
                            "l1" to Long::class.java
                        )
            } catch (_: Throwable) {
                false
            }

            ACCESSOR = if (platformOk && layoutOk) CompactStringAccessor() else null
        }

        // ===== 编码主入口：外部只依赖 ACCESSOR 是否可用 =====
        fun encode(str: String): Any = (ACCESSOR?.encode(str)) ?: str

}