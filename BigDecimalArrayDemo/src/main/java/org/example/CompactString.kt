package org.example

import org.example.LayoutChecker.UNSAFE
import sun.misc.Unsafe
import java.lang.reflect.Field
import java.nio.ByteOrder

/**
 *
 * 背景（为何要做紧凑字符串）：
 * - ERP 实体含大量“短字符串”，标准 String 占内存大头。
 * - JDK 8：String{ char[] value, int hash }；JDK 9+：String{ byte[] value, byte coder, int hash }。
 * - 标准 String 始终多出“数组对象”这一层（数组头约 16B + 数据），GC 需追踪两个对象。
 *
 * 本实现的思路：
 * - 以多个“小窗口类”（String4/12/20/28/36）内嵌存储 payload：对象头(≈12B) + header(4B) + 若干 long。
 * - ASCII：1B/char；非 ASCII：2B/char（UTF-16）。header 低 1B 为 meta（bit7=ASCII 标志，bit0..6=长度）。
 * - JDK 9+ 下，若源 String 的 coder==LATIN1，走零拷贝读取底层 byte[] 再写入；否则一次性组装为 char[] 再写入。
 *
 * 为什么不直接“把字符串转储为 byte[]/char[] 数组”更省？
 * - 忽略了数组对象头：byte[]/char[] 都要额外付出约 16B（数组头=12B + length4B，整体 8B 对齐）。
 * - 在 0..35 字符范围，按 8B 对齐后：本实现要么比数组更小 8B，要么与数组打平，几乎没有“更大”的区间；
 *   并且本实现只有一个对象，更少 GC 压力，更好的局部性。
 * - 试图“零拷贝地把数组变回 String”需要改写 JDK 内部/final 字段：JDK 8 就不安全，JDK 9+ 更脆弱（还涉及 coder）。
 *
 * JDK 9+ 与端序（UTF-16BE）：
 * - JDK 9+ 的 String 在 coder==UTF16 时以 UTF-16 BE 形式存 byte[]。
 * - 为保证后续读取统一且无需 charset 栈，本实现选择“导入时一次性把两字节组装为 char 值”（小端 CPU 假设），
 *   之后所有路径都当作 JVM 本机 char 语义做内存搬运。
 *
 * 运行时假设与开关：
 * - 小端 CPU（ByteOrder.LITTLE_ENDIAN）；若非小端，则回退为原 String。
 * - 需要 `--add-opens java.base/java.lang=ALL-UNNAMED` 以允许反射/Unsafe 访问，否则自动回退为返回原 String。
 *
 * 维护者注意（别犯的错）：
 * - ❌ 不要用 Unsafe.copyMemory(数组 → 普通对象)：JDK 9+ 多数实现会直接抛参数非法（形态不被允许），而非“对齐”问题。
 * - ✅ 用 getLong/putLong（辅以 int/short/byte）分块搬运数组→对象，或对象→数组。
 * - ❌ 不要在任意场景下尝试“获取堆对象地址并按地址 memcpy”，HotSpot 没有稳定 pin 机制。
 */
object CompactStringUtils {

    // ========================
    //   数据对象及派生类型
    // ========================
    private abstract class CompactString {
        // 只有一个字段：header（低 1 字节为 meta；高 3 字节被 payload 复用）。
        // 小端前提：读取 Int 的最低 8 位就是 meta。
        var header: Int = 0

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

        fun fromChars(chars: CharArray) {
            val acc = compactStringAccessor ?: error("CompactString accessor not available")
            acc.setRawChars(this, chars)
        }

        /** JDK9+ LATIN1 或 JDK 8 压缩版， 快速路径：直接从 byte[] 写入（ASCII）。*/
        fun fromAsciiBytes(bytes: ByteArray) {
            val acc = compactStringAccessor ?: error("CompactString accessor not available")
            acc.setAscii(this, bytes)
        }
    }

    // —— 子类：整块窗口总大小（header(4) + 若干 long*8）；payload 有效容量 = 窗口 - 1
    private class String4 : CompactString() {
        override val compactStringAccessor get() = cs4Accessor
    }

    private class String12 : CompactString() {
        @Suppress("unused")
        private var l0: Long = 0L
        override val compactStringAccessor get() = cs12Accessor
    }

    private class String20 : CompactString() {
        @Suppress("unused")
        private var l0: Long = 0L

        @Suppress("unused")
        private var l1: Long = 0L
        override val compactStringAccessor get() = cs20Accessor
    }

    private class String28 : CompactString() {
        @Suppress("unused")
        private var l0: Long = 0L

        @Suppress("unused")
        private var l1: Long = 0L

        @Suppress("unused")
        private var l2: Long = 0L
        override val compactStringAccessor get() = cs28Accessor
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
        override val compactStringAccessor get() = cs36Accessor
    }

    /**
     * 结构与职责：
     * - 针对每个窗口类（String4/12/20/28/36）缓存 header 偏移与窗口总大小，避免重复计算。
     * - 提供 setAscii()/getRawAsciiBytes() 与 setRawChars()/getRawChars()：仅做“结构化内存搬运”，不做编码。
     *
     * header 打包规则（小端）：
     * - header(4B) 的最低 1B 为 meta（bit7=ASCII，bit0..6=长度），其余高 3B复用为首个 payload 的字节位（减少一次写）。
     *
     * 为什么不用 Unsafe.copyMemory(数组→对象)：
     * - JDK 9+ 对该重载的“形态”做了收紧，数组↔普通对象常直接报参数非法（与对齐无关）。
     * - 为兼容 JDK 8/9+/Android，实现采用“分块 get/put”：先写尽可能多的 long，再写 int/short/byte。
     *
     * JNI / pinning 提醒（与 .NET fixed 的区别）：
     * - 进入 JNI 并不会暂停 GC；JNI 层拿到的是“引用句柄”，对象可被移动。
     * - 仅有 *Critical* APIs（GetPrimitiveArrayCritical/GetStringCritical）在“极短窗口”内提供稳定内存；
     *   JVM 可能选择“真 pin”或“拷贝一个临时缓冲”，要求窗口内不得阻塞/分配/回调。
     * - Java 没有 .NET 的 `fixed` 或 GCHandle.Pinned：HotSpot 不鼓励 pin 堆对象。
     * - 需要稳定地址请放到 off-heap（DirectByteBuffer/FFM MemorySegment），或用 *Critical* 仅在数组/字符串上做短时操作。
     *
     * 细节注意：
     * - 数组侧偏移必须以 Unsafe.ARRAY_*_BASE_OFFSET 为基准；不要硬编码常量。
     * - 先写 packed header，再写余量；写入前严格校验容量（1 + len 或 1 + 2*len <= windowBytes）。
     */
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
        fun setAscii(obj: CompactString, bytes: ByteArray) {
            require(1 + bytes.size <= windowBytes) { "ascii overflow total=${1 + bytes.size} cap=$windowBytes" }
            // 手动将 标志位,size, 前三个Byte 放入 header
            val b0 = (0x80 or bytes.size) and 0xFF
            val b1 = if (bytes.size > 0) bytes[0].toInt() and 0xFF else 0
            val b2 = if (bytes.size > 1) bytes[1].toInt() and 0xFF else 0
            val b3 = if (bytes.size > 2) bytes[2].toInt() and 0xFF else 0
            val packed = (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
            obj.header = packed
            if (bytes.size > 3) {
                val rem = bytes.size - 3
                bulkPutFromByteArray(obj, nextFieldOffset, bytes, 3, rem)
            }
            UNSAFE.storeFence()
        }

        fun getRawAsciiBytes(obj: CompactString, length: Int): ByteArray {
            require(1 + length <= windowBytes)
            val out = ByteArray(length)
            val packed = obj.header
            if (length > 0) out[0] = ((packed ushr 8) and 0xFF).toByte()
            if (length > 1) out[1] = ((packed ushr 16) and 0xFF).toByte()
            if (length > 2) out[2] = ((packed ushr 24) and 0xFF).toByte()
            if (length > 3) {
                val rem = length - 3
                // 替换掉 copyMemory(obj → array)
                bulkGetToByteArray(obj, nextFieldOffset, out, 3, rem)
            }
            return out
        }

        // === UTF-16 ===
        fun setRawChars(obj: CompactString, chars: CharArray) {
            val size = chars.size
            require(1 + 2 * size <= windowBytes) { "utf16 overflow total=${1 + 2 * size} cap=$windowBytes" }

            // 手动将 size、 第一个 char 放入 header, 放弃 一个 byte
            val meta = (size     and 0xFF)
            if (size == 0) {
                obj.header = meta
                return
            }
            val c0 = chars[0].code
            val b1 = c0          and 0xFF // LE
            val b2 = (c0 ushr 8) and 0xFF
            val packed = (b2 shl 16) or (b1 shl 8) or meta
            obj.header = packed
            if (size > 1) {
                val remBytes = (size - 1) * 2
                bulkPutFromCharArray(obj, nextFieldOffset, chars, 1, remBytes)
            }
            UNSAFE.storeFence()
        }

        fun getRawChars(obj: CompactString, length: Int): CharArray {
            require(1 + 2 * length <= windowBytes)

            val out = CharArray(length)
            if (length == 0) return out
            val packed = obj.header
            val lo = (packed ushr 8)  and 0xFF
            val hi = (packed ushr 16) and 0xFF
            out[0] = ((hi shl 8) or lo).toChar()
            if (length > 1) {
                val remBytes = (length - 1) * 2
                bulkGetToCharArray(obj, nextFieldOffset, out, 1, remBytes)
            }
            return out
        }

        private fun bulkGetToByteArray(
            srcObj: Any, srcOff: Long,
            dest: ByteArray, destPos: Int,
            len: Int
        ) {
            var off = 0
            while (off + 8 <= len) {
                val v = UNSAFE.getLong(srcObj, srcOff + off.toLong())
                UNSAFE.putLong(dest, BYTE_ARRAY_BASE_OFFSET + destPos + off.toLong(), v)
                off += 8
            }
            if (off + 4 <= len) {
                val v = UNSAFE.getInt(srcObj, srcOff + off.toLong())
                UNSAFE.putInt(dest, BYTE_ARRAY_BASE_OFFSET + destPos + off.toLong(), v)
                off += 4
            }
            if (off + 2 <= len) {
                val v = UNSAFE.getShort(srcObj, srcOff + off.toLong())
                UNSAFE.putShort(dest, BYTE_ARRAY_BASE_OFFSET + destPos + off.toLong(), v)
                off += 2
            }
            if (off < len) {
                val v = UNSAFE.getByte(srcObj, srcOff + off.toLong())
                UNSAFE.putByte(dest, BYTE_ARRAY_BASE_OFFSET + destPos + off.toLong(), v)
            }
        }

        private fun bulkGetToCharArray(
            srcObj: Any, srcOff: Long,
            dest: CharArray, startIndex: Int,
            bytes: Int
        ) {
            var off = 0
            val destBase = CHAR_ARRAY_BASE_OFFSET + startIndex * 2L
            while (off + 8 <= bytes) {
                val v = UNSAFE.getLong(srcObj, srcOff + off.toLong())
                UNSAFE.putLong(dest, destBase + off.toLong(), v)
                off += 8
            }
            if (off + 4 <= bytes) {
                val v = UNSAFE.getInt(srcObj, srcOff + off.toLong())
                UNSAFE.putInt(dest, destBase + off.toLong(), v)
                off += 4
            }
            if (off + 2 <= bytes) {
                val v = UNSAFE.getShort(srcObj, srcOff + off.toLong())
                UNSAFE.putShort(dest, destBase + off.toLong(), v)
                // off += 2 // 最后 2B 可不再继续
            }
        }

        /**
         * 目的：
         * - 以 getLong/putLong + getInt/putInt + getShort/putShort 的分块方式，将数组内容高效写入普通对象字段区。
         * - 避免使用 Unsafe.copyMemory(数组→对象) 在 JDK 9+ 下触发“参数非法”的形态校验。
         *
         * 实现要点：
         * - 从 ARRAY_*_BASE_OFFSET + 起始偏移开始，尽量用 8B 步长搬运；尾部用 4/2/1 处理。
         * - 目标偏移由 objectFieldOffset 或已缓存偏移给出；不要猜测 16/24/… 这类“看起来对齐”的数字。
         * - 这是 HotSpot 上最稳的“堆内 memcpy”替代，JIT 易于内联与矢量化。
         */
        private fun bulkPutFromByteArray(
            destObj: Any, destOff: Long,
            src: ByteArray, @Suppress("SameParameterValue") srcPos: Int,
            len: Int) {
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

        private fun bulkPutFromCharArray(
            destObj: Any, destOff: Long,
            src: CharArray, @Suppress("SameParameterValue") startIndex: Int,
            bytes: Int) {
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
        if (sAccessor == null) return null
        return try {
            CompactStringAccessor(clazz, maxByteLength)
        } catch (_: Throwable) {
            null
        }
    }

    private interface StringAccessor {
        fun tryGetLatin1Bytes(str : String) : ByteArray?
        fun getChars(str : String) : CharArray
    }

    private fun createStringAccessor(): StringAccessor? {
        // 只能小端；大端下 header 低字节位置不同
        if (!LayoutChecker.isLittleEndian()) return null

        // 先尝试 JDK9+（更常见），失败再回退到 JDK8。
        return createJdk9StringAccessor() ?: createJdk8StringAccessor()
    }

    private class Jdk9StringAccessor(
        private val valueOffset: Long,
        private val coderOffset: Long
    ) : StringAccessor {

        override fun tryGetLatin1Bytes(str: String): ByteArray? {
            return if (str.coder == CODER_LATIN1) str.value else null
        }

        private val String.value : ByteArray get() = UNSAFE.getObject(this, valueOffset) as ByteArray

        private val String.coder: Byte get() = UNSAFE.getByte(this, coderOffset)

        override fun getChars(str: String): CharArray {
            require(str.coder != CODER_LATIN1)
            val bytes = str.value
            val length = bytes.size ushr 1
            val out = CharArray(length)
            var bi = 0
            var ci = 0
            while (ci < length) {
                val lo = bytes[bi    ].toInt() and 0xFF
                val hi = bytes[bi + 1].toInt() and 0xFF
                out[ci] = ((hi shl 8) or lo).toChar()
                bi += 2; ci += 1
            }
            return out
        }

        companion object {
            private const val CODER_LATIN1: Byte = 0
            // private const val CODER_UTF16: Byte = 1
        }
    }

    private fun createJdk9StringAccessor(): StringAccessor? {
        // JDK9+ String: value(byte[]), code(byte), hash(int), hashIsZero(boolean)
        if (LayoutChecker.verifyLayout(
                String::class.java,
                "value" to ByteArray::class.java,
                "coder" to Byte::class.java,
                "hash" to Int::class.java,
                "hashIsZero" to Boolean::class.java)) {
            val valueField = LayoutChecker.tryGetField(String::class.java, "value")
            val coderField = LayoutChecker.tryGetField(String::class.java, "coder")

            if (valueField != null && coderField != null) {
                //println(" ........ JDK 9+ ..........")
                return Jdk9StringAccessor(
                    UNSAFE.objectFieldOffset(valueField),
                    UNSAFE.objectFieldOffset(coderField),
                )
            }
        }

        return null
    }

    // String 访问器：仅在 JDK8( value: char[], hash: int ) 且小端时启用
    private class Jdk8StringAccessor(private val valueOffset: Long) : StringAccessor {
        override fun tryGetLatin1Bytes(str: String): ByteArray? {
            val chars = str.value
            for (c in chars) if (c.code > 0x7F) return null
            val out = ByteArray(chars.size)
            for (i in chars.indices) out[i] = (chars[i].code and 0x7F).toByte()
            return out
        }

        private val String.value : CharArray get() = UNSAFE.getObject(this, valueOffset) as CharArray

        override fun getChars(str: String): CharArray {
            return str.value
        }
    }

    private fun createJdk8StringAccessor(): StringAccessor? {
        // JDK8 String: value(char[]), hash(int)
        if (LayoutChecker.verifyLayout(
                String::class.java,
                "value" to CharArray::class.java,
                "hash" to Int::class.java)) {
            val valueField = LayoutChecker.tryGetField(String::class.java, "value")

            if (valueField != null) {
                //println(" ........ JDK 8 ..........")
                return Jdk8StringAccessor(UNSAFE.objectFieldOffset(valueField))
            }
        }

        //println(" ........ Error ..........")
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
    private val sAccessor: StringAccessor? = createStringAccessor()

    // 对应窗口尺寸的访问器
    @JvmStatic
    private val cs4Accessor: CompactStringAccessor? = createCompactStringAccessor(String4::class.java, 4)

    @JvmStatic
    private val cs12Accessor: CompactStringAccessor? = createCompactStringAccessor(String12::class.java, 12)

    @JvmStatic
    private val cs20Accessor: CompactStringAccessor? = createCompactStringAccessor(String20::class.java, 20)

    @JvmStatic
    private val cs28Accessor: CompactStringAccessor? = createCompactStringAccessor(String28::class.java, 28)

    @JvmStatic
    private val cs36Accessor: CompactStringAccessor? = createCompactStringAccessor(String36::class.java, 36)

    private const val EMPTY_STRING = ""

    /**
     * 快速路径与回退：
     * - 运行环境不满足（非小端、无 --add-opens、布局探测失败）→ 直接返回原 String。
     * - 空串 → 共享同一实例；超长（>MAX_CHAR_LENGTH）→ 返回原 String。
     *
     * 选择窗口：
     * - 总字节 = 1B(meta) + n（ASCII）或 1B + 2n（UTF-16），按 4/12/20/28/36 容量择最小可容纳窗口。
     *
     * JDK 9+ 优化：
     * - coder==LATIN1：直接读取源 String 的 byte[]（零拷贝获取源数据），转换为内部 ASCII 表示。
     * - UTF-16：一次性组装为 char[]，后续内存搬运不再区分 JDK 版本。
     *
     * 内存对比小抄（与 byte[]）：
     * - 0..3/8..11/16..19/32..35 区间：本实现比 byte[] 省约 8B；
     * - 4..7/12..15/20..27/28..31：与 byte[] 打平；
     * - char[] 路径更偏向本实现（数组每元素 2B）。
     */
    fun encode(str: String?): Any? {
        // 不兼容 / 超长：直接返回；空串复用同一实例
        if (sAccessor == null || str == null || str.length > MAX_CHAR_LENGTH) return str
        if (str.isEmpty()) return EMPTY_STRING

        val bytes = sAccessor.tryGetLatin1Bytes(str)

        val totalBytes = 1 + if (bytes != null) str.length else str.length * 2
        val result: CompactString = when {
            totalBytes <= 4 -> String4()
            totalBytes <= 12 -> String12()
            totalBytes <= 20 -> String20()
            totalBytes <= 28 -> String28()
            totalBytes <= 36 -> String36()
            else -> return str
        }

        if (bytes != null) {
            result.fromAsciiBytes(bytes)
        } else {
            result.fromChars(sAccessor.getChars(str))
        }

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

    fun tryGetField(clazz: Class<*>, name: String): Field? {
        return try {
            clazz.getDeclaredField(name).apply { isAccessible = true }
        } catch (_: Throwable) {
            null
        }
    }
}