package org.example

import org.example.LayoutChecker.UNSAFE
import sun.misc.Unsafe
import java.lang.reflect.Field
import java.nio.ByteOrder

/**
 * CompactStringUtils
 *  - 仅支持小端 + JDK8 String(value: char[], hash: int)。
 *  - 充分复用 header:Int 的高 3 字节作为 payload（payload 从 header+1 开始）。
 *  - ASCII 按 1B/char；非 ASCII 按 UTF-16 2B/char，并把第一个 char(2B)塞进 header。
 *  - 为了降低 GC 压力，这里只做四/五档容量（4/12/20/28/36 字节窗口）。
 *
 * 注意：避免使用 copyMemory(数组→对象) 写入**字段中部**或跨多字段无对齐的情况。
 * 这里采用“putLong/putInt/putShort/putByte 分段写”的方式，既规避 HotSpot 的参数校验，
 * 也保证在 x86_64/ARM64 上有良好吞吐（分段最多 32B）。
 */
object CompactStringUtils {

    // ========================
    //   数据对象及派生类型
    // ========================
    private abstract class CompactString {
        // 只有一个字段：header（低 1 字节为 meta；高 3 字节被 payload 复用）。
        // 小端前提：读取 Int 的最低 8 位就是 meta。
        @Suppress("unused")
        private var header: Int = 0

        protected abstract val compactStringAccessor: CompactStringAccessor?

        override fun toString(): String {
            val acc = compactStringAccessor ?: error("CompactString accessor not available")
            val meta = header
            val isAscii = (meta and 0x80) != 0
            val len = meta and 0x7F
            return if (isAscii) {
                String(acc.getRawAsciiBytes(this, len), 0, len, Charsets.US_ASCII)
            } else {
                String(acc.getRawChars(this, len))
            }
        }

        fun fromString(ascii: Boolean, chars: CharArray) {
            val acc = compactStringAccessor ?: error("CompactString accessor not available")
            val meta = ((if (ascii) 0x80 else 0x00) or chars.size) and 0xFF
            // 先把 meta 放进 header 的低 8 位（其余位会被 setAscii/setUtf16 打包覆盖）
            this.header = meta

            if (ascii) {
                val bytes = ByteArray(chars.size)
                for (i in chars.indices) bytes[i] = (chars[i].code and 0x7F).toByte()
                acc.setAscii(this, meta, bytes)
            } else {
                acc.setRawChars(this, chars)
            }
        }
    }

    // —— 子类：整块窗口总大小（header(4) + 若干 long*8）；payload 有效容量 = 窗口 - 1
    private class String4 : CompactString() {
        override val compactStringAccessor get() = string4Accessor
    }

    private class String12 : CompactString() {
        @Suppress("unused")
        private var l0: Long = 0L
        override val compactStringAccessor get() = string12Accessor
    }

    private class String20 : CompactString() {
        @Suppress("unused")
        private var l0: Long = 0L
        @Suppress("unused")
        private var l1: Long = 0L
        override val compactStringAccessor get() = string20Accessor
    }

    private class String28 : CompactString() {
        @Suppress("unused")
        private var l0: Long = 0L
        @Suppress("unused")
        private var l1: Long = 0L
        @Suppress("unused")
        private var l2: Long = 0L
        override val compactStringAccessor get() = string28Accessor
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
        override val compactStringAccessor get() = string36Accessor
    }

    // ========================
    //   访问器：按类缓存偏移与容量
    // ========================
    private class CompactStringAccessor(clazz: Class<*>, val windowBytes: Int) {
        private val headerOffset: Long
        private val nextFieldOffset: Long

        init {
            val headerField = findHeaderField(clazz)
            headerOffset = UNSAFE.objectFieldOffset(headerField)
            nextFieldOffset = headerOffset + 4L
        }

        // === ASCII ===
        @Suppress("ReplaceSizeCheckWithIsNotEmpty")
        fun setAscii(obj: CompactString, meta: Int, bytes: ByteArray) {
            require(1 + bytes.size <= windowBytes) { "ascii overflow total=${1 + bytes.size} cap=$windowBytes" }
            val b0 = meta and 0xFF
            val b1 = if (bytes.size > 0) bytes[0].toInt() and 0xFF else 0
            val b2 = if (bytes.size > 1) bytes[1].toInt() and 0xFF else 0
            val b3 = if (bytes.size > 2) bytes[2].toInt() and 0xFF else 0
            val packed = (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
            UNSAFE.putInt(obj, headerOffset, packed)
            if (bytes.size > 3) {
                val rem = bytes.size - 3
                bulkPutFromByteArray(obj, nextFieldOffset, bytes, 3, rem)
            }
        }

        fun getRawAsciiBytes(obj: CompactString, length: Int): ByteArray {
            require(1 + length <= windowBytes)
            val out = ByteArray(length)
            val packed = UNSAFE.getInt(obj, headerOffset)
            if (length > 0) out[0] = ((packed ushr 8)  and 0xFF).toByte()
            if (length > 1) out[1] = ((packed ushr 16) and 0xFF).toByte()
            if (length > 2) out[2] = ((packed ushr 24) and 0xFF).toByte()
            if (length > 3) {
                val rem = length - 3
                UNSAFE.copyMemory(obj, nextFieldOffset, out, BYTE_ARRAY_BASE_OFFSET + 3, rem.toLong())
            }
            return out
        }

        // === UTF-16 ===
        fun setRawChars(obj: CompactString, chars: CharArray) {
            require(1 + 2 * chars.size <= windowBytes) { "utf16 overflow total=${1 + 2 * chars.size} cap=$windowBytes" }
            val len = chars.size
            if (len == 0) {
                val meta = (UNSAFE.getInt(obj, headerOffset) and 0xFF)
                UNSAFE.putInt(obj, headerOffset, meta)
                return
            }
            val c0 = chars[0].code
            val meta = (UNSAFE.getInt(obj, headerOffset) and 0xFF)
            val b1 =  c0         and 0xFF // LE
            val b2 = (c0 ushr 8) and 0xFF
            val packed = (0 shl 24) or (b2 shl 16) or (b1 shl 8) or meta
            UNSAFE.putInt(obj, headerOffset, packed)
            if (len > 1) {
                val remBytes = (len - 1) * 2
                bulkPutFromCharArray(obj, nextFieldOffset, chars, 1, remBytes)
            }
        }

        fun getRawChars(obj: CompactString, length: Int): CharArray {
            require(1 + 2 * length <= windowBytes)
            val out = CharArray(length)
            if (length == 0) return out
            val packed = UNSAFE.getInt(obj, headerOffset)
            val lo = (packed ushr 8)  and 0xFF
            val hi = (packed ushr 16) and 0xFF
            out[0] = ((hi shl 8) or lo).toChar()
            if (length > 1) {
                val remBytes = (length - 1) * 2
                UNSAFE.copyMemory(obj, nextFieldOffset, out, CHAR_ARRAY_BASE_OFFSET + 2, remBytes.toLong())
            }
            return out
        }

        // —— 写入助手：避免数组→对象的 copyMemory 触发 HotSpot 校验 ——
        private fun bulkPutFromByteArray(destObj: Any, destOff: Long, src: ByteArray, srcPos: Int, len: Int) {
            var off = 0
            while (off + 8 <= len) {
                val v = UNSAFE.getLong(src, BYTE_ARRAY_BASE_OFFSET + srcPos + off.toLong())
                UNSAFE.putLong(destObj, destOff + off, v)
                off += 8
            }
            if (off + 4 <= len) {
                val v = UNSAFE.getInt(src, BYTE_ARRAY_BASE_OFFSET + srcPos + off.toLong())
                UNSAFE.putInt(destObj, destOff + off, v)
                off += 4
            }
            if (off + 2 <= len) {
                val v = UNSAFE.getShort(src, BYTE_ARRAY_BASE_OFFSET + srcPos + off.toLong())
                UNSAFE.putShort(destObj, destOff + off, v)
                off += 2
            }
            if (off < len) {
                val v = UNSAFE.getByte(src, BYTE_ARRAY_BASE_OFFSET + srcPos + off.toLong())
                UNSAFE.putByte(destObj, destOff + off, v)
            }
        }

        private fun bulkPutFromCharArray(destObj: Any, destOff: Long, src: CharArray, startIndex: Int, bytes: Int) {
            var off = 0
            val base = CHAR_ARRAY_BASE_OFFSET + startIndex * 2L
            while (off + 8 <= bytes) {
                val v = UNSAFE.getLong(src, base + off)
                UNSAFE.putLong(destObj, destOff + off, v)
                off += 8
            }
            if (off + 4 <= bytes) {
                val v = UNSAFE.getInt(src, base + off)
                UNSAFE.putInt(destObj, destOff + off, v)
                off += 4
            }
            if (off + 2 <= bytes) {
                val v = UNSAFE.getShort(src, base + off)
                UNSAFE.putShort(destObj, destOff + off, v)
                //off += 2
            }
        }
    }

    // String 访问器：仅在 JDK8( value: char[], hash: int ) 且小端时启用
    private class StringAccessor(private val valueOffset: Long) {
        fun getChars(str: String): CharArray = UNSAFE.getObject(str, valueOffset) as CharArray
    }

    private fun findHeaderField(clazz: Class<*>): Field {
        var c: Class<*>? = clazz
        while (c != null) {
            try {
                val f = c.getDeclaredField(HEADER_NAME)
                f.isAccessible = true
                return f
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
        throw NoSuchFieldException("header not found in hierarchy of ${clazz.name}")
    }

    private fun createCompactStringAccessor(clazz: Class<*>, maxByteLength: Int): CompactStringAccessor? {
        // 只有在 StringAccessor 就绪时才创建（encode 统一走零分配路径）
        if (accessor == null) return null
        return try {
            CompactStringAccessor(clazz, maxByteLength)
        } catch (_: Throwable) {
            null
        }
    }

    private fun createStringAccessor(): StringAccessor? {
        // 只能小端；大端下 header 低字节位置不同
        if (!LayoutChecker.isLittleEndian()) return null
        // JDK8 String: value(char[]), hash(int)
        if (!LayoutChecker.verifyLayout(
                String::class.java,
                "value" to CharArray::class.java,
                "hash" to Int::class.java
            )
        ) return null
        val valueField = try {
            String::class.java.getDeclaredField("value").apply { isAccessible = true }
        } catch (_: Throwable) {
            null
        }
        if (valueField != null) {
            val valueOffset = UNSAFE.objectFieldOffset(valueField)
            return StringAccessor(valueOffset)
        }
        return null
    }

    // ========================
    //   常量与运行时能力（按声明顺序初始化）
    // ========================
    private val BYTE_ARRAY_BASE_OFFSET: Long = Unsafe.ARRAY_BYTE_BASE_OFFSET.toLong()
    private val CHAR_ARRAY_BASE_OFFSET: Long = Unsafe.ARRAY_CHAR_BASE_OFFSET.toLong()

    // 我们不打算压缩到 127；为了平衡 GC 与收益，限制到 35（ASCII 支持到 35，非 ASCII 最多 17）
    private const val MAX_CHAR_LENGTH = 35
    private const val HEADER_NAME = "header"

    // 运行时访问器（必须先于 stringNAccessor 初始化）
    @JvmStatic
    private val accessor: StringAccessor? = createStringAccessor()

    // 对应窗口尺寸的访问器
    @JvmStatic
    private val string4Accessor: CompactStringAccessor? = createCompactStringAccessor(String4::class.java, 4)
    @JvmStatic
    private val string12Accessor: CompactStringAccessor? = createCompactStringAccessor(String12::class.java, 12)
    @JvmStatic
    private val string20Accessor: CompactStringAccessor? = createCompactStringAccessor(String20::class.java, 20)
    @JvmStatic
    private val string28Accessor: CompactStringAccessor? = createCompactStringAccessor(String28::class.java, 28)
    @JvmStatic
    private val string36Accessor: CompactStringAccessor? = createCompactStringAccessor(String36::class.java, 36)

    private const val EMPTY_STRING = ""

    /**
     * 尝试压缩；若运行时不兼容或容量不足，则返回原生 String。
     */
    fun encode(str: String?): Any? {
        // 不兼容 / 超长：直接返回；空串复用同一实例
        if (str == null || accessor == null || str.length > MAX_CHAR_LENGTH) return str
        if (str.isEmpty()) return EMPTY_STRING

        var ascii = true
        for (c in str) {
            if (c.code > 0x7F) {
                ascii = false; break
            }
        }

        val totalBytes = 1 + if (ascii) str.length else str.length * 2
        val result: CompactString = when {
            totalBytes <= 4 -> String4()
            totalBytes <= 12 -> String12()
            totalBytes <= 20 -> String20()
            totalBytes <= 28 -> String28()
            totalBytes <= 36 -> String36()
            else -> return str
        }

        val chars = accessor.getChars(str)
        result.fromString(ascii, chars)
        return result
    }
}

// ========================
//   运行时环境与布局检查
// ========================
internal object LayoutChecker {
    val UNSAFE: Unsafe = run {
        val f = Unsafe::class.java.getDeclaredField("theUnsafe")
        f.isAccessible = true
        f.get(null) as Unsafe
    }

    fun isLittleEndian(): Boolean = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN

    /** 校验字段名与类型（严格相等；多一个字段也视为不兼容） */
    fun verifyLayout(clazz: Class<*>, vararg expected: Pair<String, Class<*>>): Boolean {
        return try {
            val nonStatic = clazz.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
            if (nonStatic.size != expected.size) return false
            for ((name, type) in expected) {
                val ok = nonStatic.any { it.name == name && it.type == type }
                if (!ok) return false
            }
            true
        } catch (_: Throwable) {
            false
        }
    }
}