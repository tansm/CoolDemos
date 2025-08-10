package org.example

import org.example.LayoutChecker.UNSAFE
import sun.misc.Unsafe
import java.lang.reflect.Field
import java.nio.ByteOrder

/**
 * CompactStringUtils
 *  - 仅支持小端 + JDK8 String(value: char[], hash: int)。
 *  - 充分复用 header:Int 的高 3 字节作为 payload（payload 从 header+1 开始）。
 *  - ASCII 按 1B/char；非 ASCII 按 UTF-16 2B/char。
 *  - 为了降低 GC 压力，这里只做四档/五档容量（4/12/20/28/36 字节窗口）。
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
            val accessor = compactStringAccessor ?: error("CompactString accessor not available on this runtime")
            val meta = header
            val isAscii = (meta and 0x80) != 0  // 最高位 = 1 表示 ASCII
            val length = meta and 0x7F          // 0..127
            return if (isAscii) {
                String(accessor.getRawAsciiBytes(this, length), 0, length, Charsets.US_ASCII)
            } else {
                String(accessor.getRawChars(this, length))
            }
        }

        fun fromString(ascii: Boolean, chars: CharArray) {
            val accessor = compactStringAccessor ?: error("CompactString accessor not available on this runtime")
            // meta：bit7=ASCII 标志；低 7 位 = length
            header = ((if (ascii) 0x80 else 0x00) or chars.size) and 0xFF

            if (ascii) {
                // char->byte 需要显式窄化；构造一次性 byte[] 再 copyMemory
                val bytes = ByteArray(chars.size)
                for (i in chars.indices) bytes[i] = (chars[i].code and 0x7F).toByte()
                accessor.setRawAsciiBytes(this, bytes)
            } else {
                accessor.setRawChars(this, chars)
            }
        }
    }

    // —— 子类：整块窗口总大小（header(4) + 若干 long*8）；payload 有效容量 = 窗口 - 1
    private class String4  : CompactString() { override val compactStringAccessor get() = string4Accessor }
    private class String12 : CompactString() { @Suppress("unused") private var l0: Long = 0L; override val compactStringAccessor get() = string12Accessor }
    private class String20 : CompactString() { @Suppress("unused") private var l0: Long = 0L; @Suppress("unused") private var l1: Long = 0L; override val compactStringAccessor get() = string20Accessor }
    private class String28 : CompactString() { @Suppress("unused") private var l0: Long = 0L; @Suppress("unused") private var l1: Long = 0L; @Suppress("unused") private var l2: Long = 0L; override val compactStringAccessor get() = string28Accessor }
    private class String36 : CompactString() { @Suppress("unused") private var l0: Long = 0L; @Suppress("unused") private var l1: Long = 0L; @Suppress("unused") private var l2: Long = 0L; @Suppress("unused") private var l3: Long = 0L; override val compactStringAccessor get() = string36Accessor }

    // ========================
    //   访问器：按类缓存偏移与容量
    // ========================
    private class CompactStringAccessor(clazz: Class<*>, val maxByteLength: Int) {
        private val payloadOffset: Long
        init {
            // 注意：header 在父类 CompactString 上，不能用 clazz.getDeclaredField("header")！
            val headerField = findHeaderField(clazz)
            payloadOffset = UNSAFE.objectFieldOffset(headerField) + 1 // 复用 header 后 3 字节
        }
        fun getRawAsciiBytes(obj: CompactString, length: Int): ByteArray {
            require(length < maxByteLength) { "ascii payload overflow: len=$length cap=$maxByteLength" }
            val dst = ByteArray(length)
            UNSAFE.copyMemory(obj, payloadOffset, dst, BYTE_ARRAY_BASE_OFFSET, length.toLong())
            return dst
        }
        fun getRawChars(obj: CompactString, length: Int): CharArray {
            val bytes = length * 2
            require(bytes < maxByteLength) { "utf16 payload overflow: bytes=$bytes cap=$maxByteLength" }
            val dst = CharArray(length)
            UNSAFE.copyMemory(obj, payloadOffset, dst, CHAR_ARRAY_BASE_OFFSET, bytes.toLong())
            return dst
        }
        fun setRawAsciiBytes(obj: CompactString, bytes: ByteArray) {
            require(bytes.size < maxByteLength) { "ascii payload overflow: len=${bytes.size} cap=$maxByteLength" }
            UNSAFE.copyMemory(bytes, BYTE_ARRAY_BASE_OFFSET, obj, payloadOffset, bytes.size.toLong())
        }
        fun setRawChars(obj: CompactString, chars: CharArray) {
            val bytes = chars.size * 2
            require(bytes < maxByteLength) { "utf16 payload overflow: bytes=$bytes cap=$maxByteLength" }
            UNSAFE.copyMemory(chars, CHAR_ARRAY_BASE_OFFSET, obj, payloadOffset, bytes.toLong())
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
        return try { CompactStringAccessor(clazz, maxByteLength) } catch (_: Throwable) { null }
    }

    private fun createStringAccessor(): StringAccessor? {
        // 只能小端；大端下 header 低字节位置不同
        if (!LayoutChecker.isLittleEndian()) return null
        // JDK8 String: value(char[]), hash(int)
        if (!LayoutChecker.verifyLayout(String::class.java, "value" to CharArray::class.java, "hash" to Int::class.java)) return null
        val valueField = try { String::class.java.getDeclaredField("value").apply { isAccessible = true } } catch (_: Throwable) { null }
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
    @JvmStatic private val accessor: StringAccessor? = createStringAccessor()

    // 对应窗口尺寸的访问器
    @JvmStatic private val string4Accessor:  CompactStringAccessor? = createCompactStringAccessor(String4::class.java, 4)
    @JvmStatic private val string12Accessor: CompactStringAccessor? = createCompactStringAccessor(String12::class.java, 12)
    @JvmStatic private val string20Accessor: CompactStringAccessor? = createCompactStringAccessor(String20::class.java, 20)
    @JvmStatic private val string28Accessor: CompactStringAccessor? = createCompactStringAccessor(String28::class.java, 28)
    @JvmStatic private val string36Accessor: CompactStringAccessor? = createCompactStringAccessor(String36::class.java, 36)

    private const val EMPTY_STRING = ""

    /**
     * 尝试压缩；若运行时不兼容或容量不足，则返回原生 String。
     */
    fun encode(str: String?): Any? {
        // 不兼容 / 超长：直接返回；空串复用同一实例
        if (str == null || accessor == null || str.length > MAX_CHAR_LENGTH) return str
        if (str.isEmpty()) return EMPTY_STRING

        // 判 ASCII（短路）
        var ascii = true
        for (c in str) { if (c.code > 0x7F) { ascii = false; break } }

        val totalBytes = 1 + if (ascii) str.length else str.length * 2
        val result: CompactString = when {
            totalBytes <= 4  -> String4()
            totalBytes <= 12 -> String12()
            totalBytes <= 20 -> String20()
            totalBytes <= 28 -> String28()
            totalBytes <= 36 -> String36()
            else -> return str // 非 ASCII 超过 17 个字符等情况
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
        } catch (_: Throwable) { false }
    }
}