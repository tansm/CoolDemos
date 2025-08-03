package org.example

import sun.misc.Unsafe
import java.math.BigDecimal
import java.nio.ByteBuffer

//region ================= fields =======================
/**
 * 描述了字段和类型两个信息。
 *
 * 从理论上来说，下面的设计更标准：
 * ```class Field<T : DataType>(val dataType: T)```
 * 但实际实现了一个版本，发现增加了不必要的复杂度，最终还是简单其间。
 */
internal abstract class Field {
    private var _offset: Int = -1  // 新的 offset 变量，代替 byteOffset 和 bitIndex

    protected val offset : Int get() = _offset

    open fun setOffset(byteOffset: Int, bitIndex : Int) {
        require(bitIndex == 0)
        _offset = byteOffset
    }

    abstract val size: Int
    abstract val alignment: Int
}

internal class BooleanField : Field() {
    fun get(buffer: ByteBuffer): Boolean = ((buffer.get(byteOffset).toInt() shr bitIndex) and 1) == 1

    fun set(buffer: ByteBuffer, value: Boolean) {
        val original = buffer.get(byteOffset).toInt()
        val updated = if (value) original or (1 shl bitIndex) else original and (1 shl bitIndex).inv()
        buffer.put(byteOffset, updated.toByte())
    }

    // 计算 byteOffset 和 bitIndex 的辅助方法
    private val byteOffset: Int
        get() = offset ushr 3  // 使用无符号右移操作代替除法，计算 byteOffset

    private val bitIndex: Int
        get() = offset and 7  // 使用与操作代替取余，计算 bitIndex

    override fun setOffset(byteOffset: Int, bitIndex: Int) {
        val offset = (byteOffset shl 3) or (bitIndex and 0x7)
        super.setOffset(offset, 0)
    }

    override val size get() = 1
    override val alignment get() = 1
}

internal class ByteField : Field() {
    fun get(buffer: ByteBuffer): Byte = buffer.get(offset)
    fun set(buffer: ByteBuffer, value: Byte) { buffer.put(offset, value) }
    override val size get() = Byte.SIZE_BYTES
    override val alignment get() = 1
}

internal class ShortField : Field() {
    fun get(buffer: ByteBuffer): Short = buffer.getShort(offset)
    fun set(buffer: ByteBuffer, value: Short) { buffer.putShort(offset, value) }
    override val size get() = Short.SIZE_BYTES
    override val alignment get() = 2
}

internal class IntField : Field() {
    fun get(buffer: ByteBuffer): Int = buffer.getInt(offset)
    fun set(buffer: ByteBuffer, value: Int) { buffer.putInt(offset, value) }
    override val size get() = Int.SIZE_BYTES
    override val alignment get() = 4
}

internal class LongField : Field() {
    fun get(buffer: ByteBuffer): Long = buffer.getLong(offset)
    fun set(buffer: ByteBuffer, value: Long) { buffer.putLong(offset, value) }
    override val size get() = Long.SIZE_BYTES
    override val alignment get() = 8
}

internal class FloatField : Field() {
    fun get(buffer: ByteBuffer): Float = buffer.getFloat(offset)
    fun set(buffer: ByteBuffer, value: Float) { buffer.putFloat(offset, value) }
    override val size get() = Float.SIZE_BYTES
    override val alignment get() = 4
}

internal class DoubleField : Field() {
    fun get(buffer: ByteBuffer): Double = buffer.getDouble(offset)
    fun set(buffer: ByteBuffer, value: Double) { buffer.putDouble(offset, value) }
    override val size get() = Double.SIZE_BYTES
    override val alignment get() = 8
}

//endregion

//region =================== SparseObjectMap ========================

/**
 * 稀疏属性索引到对象的映射，内部用 ShortArray 和 Array<Any?>，索引有序，查找用二分法。
 */
@OptIn(ExperimentalUnsignedTypes::class)
class SparseObjectMap(initialCapacity: Int = 0) {
    private var keys = UShortArray(initialCapacity)
    private var values = arrayOfNulls<Any>(initialCapacity)
    private var _size = 0

    val size: Int get() = _size

    operator fun get(key: Int): Any? {
        val k = checkKey(key)
        val idx = hybridSearch(k)
        return if (idx >= 0) values[idx] else null
    }

    operator fun set(key: Int, value: Any?) {
        val k = checkKey(key)
        if (value == null) {
            remove(key)
            return
        }
        val idx = hybridSearch(k)
        if (idx >= 0) {
            values[idx] = value
        } else {
            insertAt(-(idx + 1), k, value)
        }
    }

    fun remove(key: Int) {
        val k = checkKey(key)
        val idx = hybridSearch(k)
        if (idx >= 0) removeAt(idx)
    }

    private fun checkKey(key: Int): UShort {
        require(key in 0..0xFFFF) { "Key must be in 0..65535, but was $key" }
        return key.toUShort()
    }

    private fun hybridSearch(key: UShort): Int {
        if (_size == 0) return -1
        if ((_size > 0) && (keys[_size - 1] < key)) {
            return -_size - 1
        }
        var low = 0
        var high = _size - 1
        while (low + 32 <= high) {
            val mid = (low + high) ushr 1
            val midVal = keys[mid]
            when {
                midVal < key -> low = mid + 1
                midVal > key -> high = mid - 1
                else -> return mid
            }
        }
        var x = low
        while (x <= high) {
            val v = keys[x]
            if (v >= key) {
                if (v == key) return x
                break
            }
            x++
        }
        return -(x + 1)
    }

    private fun insertAt(index: Int, key: UShort, value: Any?) {
        ensureCapacity(_size + 1)
        if (index < _size) {
            keys.copyInto(keys, index + 1, index, _size)
            values.copyInto(values, index + 1, index, _size)
        }
        keys[index] = key
        values[index] = value
        _size++
    }

    private fun removeAt(index: Int) {
        if (index < _size - 1) {
            keys.copyInto(keys, index, index + 1, _size)
            values.copyInto(values, index, index + 1, _size)
        }
        _size--
        values[_size] = null
    }

    private fun ensureCapacity(cap: Int) {
        if (cap > keys.size) {
            val newCap = if (keys.isEmpty()) 2 else keys.size * 2
            keys = keys.copyOf(newCap)
            values = values.copyOf(newCap)
        }
    }
}

internal class ByteStorage(byteSize : Int) {
    val buffer: ByteBuffer = ByteBuffer.allocate(byteSize)
    val objectMap = SparseObjectMap()
}
//endregion

//region =================== PropertyAccessor ========================
internal abstract class PropertyAccessor {
    private var _propertyIndex : Int = -1
    val propertyIndex: Int get() = _propertyIndex
    fun resetPropertyIndex(index: Int){
        _propertyIndex = index
    }

    abstract val nullable: Boolean
    abstract val defaultValue : Any?
    abstract fun getFields(): List<Field>
    abstract fun get(buffer: ByteBuffer): Any?
    open fun get(storage: ByteStorage) : Any?{
        return get(storage.buffer)
    }
    abstract fun set(buffer: ByteBuffer, value: Any?)
    open fun set(storage: ByteStorage, value: Any?){
        set(storage.buffer, value)
    }
}

/**
 * boolean 或者 boolean? 类型的属性访问器。
 */
internal class BooleanPropertyAccessor(override val nullable: Boolean, defaultValue: Boolean) : PropertyAccessor() {
    private val _valueField = BooleanField()
    private val _definedField: BooleanField?
    private val _defaultValue: Boolean

    init {
        if (nullable) {
            if (defaultValue) {
                throw IllegalArgumentException("When nullable is true, defaultValue must be false. Current defaultValue: true")
            }
            _defaultValue = false
            _definedField = BooleanField()
        } else {
            _defaultValue = defaultValue
            // 这个地方的判断别写错了。
            _definedField = if (!defaultValue) null else BooleanField()
        }
    }

    override fun getFields() = listOfNotNull(_valueField, _definedField)

    override val defaultValue: Any?
        get() = if (nullable) null else _defaultValue

    override fun get(buffer: ByteBuffer): Any? {
        if (_definedField?.get(buffer) == false) return defaultValue
        return _valueField.get(buffer)
    }

    override fun set(buffer: ByteBuffer, value: Any?) {
        _definedField?.set(buffer, value != null)
        if (value == null) _valueField.set(buffer, _defaultValue) else _valueField.set(buffer, value as Boolean)
    }

    fun getBoolean(buffer: ByteBuffer): Boolean {
        if (_definedField?.get(buffer) == false) return _defaultValue
        return _valueField.get(buffer)
    }

    fun setBoolean(buffer: ByteBuffer, value: Boolean) {
        _valueField.set(buffer, value)
        _definedField?.set(buffer, true)
    }
}

/**
 * byte 或者 byte? 类型的属性访问器。
 */
internal class BytePropertyAccessor(override val nullable: Boolean, defaultValue: Byte) : PropertyAccessor() {
    private val _valueField = ByteField()
    private val _definedField: BooleanField?
    private val _defaultValue: Byte

    init {
        if (nullable) {
            if (defaultValue.toInt() != 0) {
                throw IllegalArgumentException("When nullable is true, defaultValue must be 0. Current defaultValue: $defaultValue")
            }
            _defaultValue = 0
            _definedField = BooleanField()
        } else {
            _defaultValue = defaultValue
            _definedField = if (defaultValue.toInt() == 0) null else BooleanField()
        }
    }

    override fun getFields() = listOfNotNull(_valueField, _definedField)

    override val defaultValue: Any?
        get() = if (nullable) null else _defaultValue

    override fun get(buffer: ByteBuffer): Any? {
        if (_definedField?.get(buffer) == false) return defaultValue
        return _valueField.get(buffer)
    }

    override fun set(buffer: ByteBuffer, value: Any?) {
        _definedField?.set(buffer, value != null)
        if (value == null) _valueField.set(buffer, _defaultValue) else _valueField.set(buffer, value as Byte)
    }

    fun getByte(buffer: ByteBuffer): Byte {
        if (_definedField?.get(buffer) == false) return _defaultValue
        return _valueField.get(buffer)
    }

    fun setByte(buffer: ByteBuffer, value: Byte) {
        _valueField.set(buffer, value)
        _definedField?.set(buffer, true)
    }
}

/**
 * short 或者 short? 类型的属性访问器。
 */
internal class ShortPropertyAccessor(override val nullable: Boolean, defaultValue: Short) : PropertyAccessor() {
    private val _valueField = ShortField()
    private val _definedField: BooleanField?
    private val _defaultValue: Short

    init {
        if (nullable) {
            if (defaultValue.toInt() != 0) {
                throw IllegalArgumentException("When nullable is true, defaultValue must be 0. Current defaultValue: $defaultValue")
            }
            _defaultValue = 0
            _definedField = BooleanField()
        } else {
            _defaultValue = defaultValue
            _definedField = if (defaultValue.toInt() == 0) null else BooleanField()
        }
    }

    override fun getFields() = listOfNotNull(_valueField, _definedField)

    override val defaultValue: Any?
        get() = if (nullable) null else _defaultValue

    override fun get(buffer: ByteBuffer): Any? {
        if (_definedField?.get(buffer) == false) return defaultValue
        return _valueField.get(buffer)
    }

    override fun set(buffer: ByteBuffer, value: Any?) {
        _definedField?.set(buffer, value != null)
        if (value == null) _valueField.set(buffer, _defaultValue) else _valueField.set(buffer, value as Short)
    }

    fun getShort(buffer: ByteBuffer): Short {
        if (_definedField?.get(buffer) == false) return _defaultValue
        return _valueField.get(buffer)
    }

    fun setShort(buffer: ByteBuffer, value: Short) {
        _valueField.set(buffer, value)
        _definedField?.set(buffer, true)
    }
}

/**
 * int 或者 int? 类型的属性访问器。
 */
internal class IntPropertyAccessor(override val nullable: Boolean, defaultValue : Int) : PropertyAccessor() {
    private val _valueField = IntField()
    // 用于表示此字段是否是 已定义值 状态，字段在初始化时此标志位字段的值是 0， 表示以 defaultValue 为准
    private val _definedField : BooleanField?
    private val _defaultValue: Int

    init {
        if (nullable) {
            // 允许 null 时，缺省值不支持设置，只能为 0
            if (defaultValue != 0) {
                throw IllegalArgumentException("When nullable is true, defaultValue must be 0. Current defaultValue: $defaultValue")
            }
            _defaultValue = 0
            _definedField = BooleanField()
        }else{
            _defaultValue = defaultValue
            // 如果不可以为 null，且缺省值是 0，直接使用缺省值，不必要标志位。这在大多数情况下能够优化。
            _definedField = if(defaultValue == 0) null else BooleanField()
        }
    }

    override fun getFields() = listOfNotNull(_valueField, _definedField)

    override val defaultValue: Any?
        get() = if(nullable) null else _defaultValue

    override fun get(buffer: ByteBuffer): Any? {
        if (_definedField?.get(buffer) == false) return defaultValue
        return _valueField.get(buffer)
    }

    override fun set(buffer: ByteBuffer, value: Any?) {
        _definedField?.set(buffer, value != null)
        if (value == null) _valueField.set(buffer, _defaultValue) else _valueField.set(buffer, value as Int)
    }

    fun getInt(buffer: ByteBuffer): Int {
        if (_definedField?.get(buffer) == false) return _defaultValue
        return _valueField.get(buffer)
    }

    fun setInt(buffer: ByteBuffer, value: Int) {
        _valueField.set(buffer, value)
        _definedField?.set(buffer, true)
    }
}

/**
 * long 或者 long? 类型的属性访问器。
 */
internal class LongPropertyAccessor(override val nullable: Boolean, defaultValue: Long) : PropertyAccessor() {
    private val _valueField = LongField()
    private val _definedField: BooleanField?
    private val _defaultValue: Long

    init {
        if (nullable) {
            if (defaultValue != 0L) {
                throw IllegalArgumentException("When nullable is true, defaultValue must be 0. Current defaultValue: $defaultValue")
            }
            _defaultValue = 0L
            _definedField = BooleanField()
        } else {
            _defaultValue = defaultValue
            _definedField = if (defaultValue == 0L) null else BooleanField()
        }
    }

    override fun getFields() = listOfNotNull(_valueField, _definedField)

    override val defaultValue: Any?
        get() = if (nullable) null else _defaultValue

    override fun get(buffer: ByteBuffer): Any? {
        if (_definedField?.get(buffer) == false) return defaultValue
        return _valueField.get(buffer)
    }

    override fun set(buffer: ByteBuffer, value: Any?) {
        _definedField?.set(buffer, value != null)
        if (value == null) _valueField.set(buffer, _defaultValue) else _valueField.set(buffer, value as Long)
    }

    fun getLong(buffer: ByteBuffer): Long {
        if (_definedField?.get(buffer) == false) return _defaultValue
        return _valueField.get(buffer)
    }

    fun setLong(buffer: ByteBuffer, value: Long) {
        _valueField.set(buffer, value)
        _definedField?.set(buffer, true)
    }
}

/**
 * float 或者 float? 类型的属性访问器。
 */
internal class FloatPropertyAccessor(override val nullable: Boolean, defaultValue: Float) : PropertyAccessor() {
    private val _valueField = FloatField()
    private val _definedField: BooleanField?
    private val _defaultValue: Float

    init {
        if (nullable) {
            if (defaultValue != 0.0f) {
                throw IllegalArgumentException("When nullable is true, defaultValue must be 0.0. Current defaultValue: $defaultValue")
            }
            _defaultValue = 0.0f
            _definedField = BooleanField()
        } else {
            _defaultValue = defaultValue
            _definedField = if (defaultValue == 0.0f) null else BooleanField()
        }
    }

    override fun getFields() = listOfNotNull(_valueField, _definedField)

    override val defaultValue: Any?
        get() = if (nullable) null else _defaultValue

    override fun get(buffer: ByteBuffer): Any? {
        if (_definedField?.get(buffer) == false) return defaultValue
        return _valueField.get(buffer)
    }

    override fun set(buffer: ByteBuffer, value: Any?) {
        _definedField?.set(buffer, value != null)
        if (value == null) _valueField.set(buffer, _defaultValue) else _valueField.set(buffer, value as Float)
    }

    fun getFloat(buffer: ByteBuffer): Float {
        if (_definedField?.get(buffer) == false) return _defaultValue
        return _valueField.get(buffer)
    }

    fun setFloat(buffer: ByteBuffer, value: Float) {
        _valueField.set(buffer, value)
        _definedField?.set(buffer, true)
    }
}

/**
 * double 或者 double? 类型的属性访问器。
 */
internal class DoublePropertyAccessor(override val nullable: Boolean, defaultValue: Double) : PropertyAccessor() {
    private val _valueField = DoubleField()
    private val _definedField: BooleanField?
    private val _defaultValue: Double

    init {
        if (nullable) {
            if (defaultValue != 0.0) {
                throw IllegalArgumentException("When nullable is true, defaultValue must be 0.0. Current defaultValue: $defaultValue")
            }
            _defaultValue = 0.0
            _definedField = BooleanField()
        } else {
            _defaultValue = defaultValue
            _definedField = if (defaultValue == 0.0) null else BooleanField()
        }
    }

    override fun getFields() = listOfNotNull(_valueField, _definedField)

    override val defaultValue: Any?
        get() = if (nullable) null else _defaultValue

    override fun get(buffer: ByteBuffer): Any? {
        if (_definedField?.get(buffer) == false) return defaultValue
        return _valueField.get(buffer)
    }

    override fun set(buffer: ByteBuffer, value: Any?) {
        _definedField?.set(buffer, value != null)
        if (value == null) _valueField.set(buffer, _defaultValue) else _valueField.set(buffer, value as Double)
    }

    fun getDouble(buffer: ByteBuffer): Double {
        if (_definedField?.get(buffer) == false) return _defaultValue
        return _valueField.get(buffer)
    }

    fun setDouble(buffer: ByteBuffer, value: Double) {
        _valueField.set(buffer, value)
        _definedField?.set(buffer, true)
    }
}

internal class BigDecimalPropertyAccessor(override val nullable: Boolean) : PropertyAccessor() {
    private val _intCompactField = LongField()
    private val _scaleField = ByteField()
    private val _definedField: BooleanField? = if (nullable) BooleanField() else null

    override fun getFields() = listOfNotNull(_intCompactField, _scaleField, _definedField)

    // 为降低复杂度，BigDecimal 不支持自定义的缺省值。 nullable = false 时，intCompact 和 scale 正好都是 0.
    override val defaultValue: Any? get() = defaultBigDecimalValue
    private val defaultBigDecimalValue : BigDecimal? get() = if (nullable) null else BigDecimal.ZERO

    override fun get(buffer: ByteBuffer): Any? {
        throw RuntimeException("not support")
    }

    override fun get(storage: ByteStorage): Any? {
        return getBigDecimal(storage)
    }

    override fun set(buffer: ByteBuffer, value: Any?) {
        throw RuntimeException("not support")
    }

    override fun set(storage: ByteStorage, value: Any?) {
        setBigDecimal(storage, value as BigDecimal?)
    }

    fun getBigDecimal(storage: ByteStorage): BigDecimal? {
        val buffer = storage.buffer
        val objectMap = storage.objectMap

        if (_definedField?.get(buffer) == false) return defaultBigDecimalValue

        val intCompact = _intCompactField.get(buffer)
        return if (intCompact == INFLATED) {
            // 从 objectMap 中获取值，如果获取的值是 null, 返回 默认值
            val obj: BigDecimal? = objectMap[propertyIndex] as BigDecimal?
            obj ?: defaultBigDecimalValue
        } else {
            // 从数据中恢复, scale 可以是负数也可以是正数
            BigDecimal.valueOf(intCompact, _scaleField.get(buffer).toInt())
        }
    }

    fun setBigDecimal(storage: ByteStorage, value: BigDecimal?) {
        val buffer = storage.buffer
        val objectMap = storage.objectMap

        _definedField?.set(buffer, value != null)
        if (value == null) {
            _intCompactField.set(buffer, 0L)
            _scaleField.set(buffer, 0)
            objectMap[propertyIndex] = null
            return
        }

        val intCompact =  UNSAFE.getLong(value, INT_COMPACT_OFFSET);
        val scale = value.scale()

        if (intCompact != INFLATED && (scale in Byte.MIN_VALUE .. Byte.MAX_VALUE)) {
            _intCompactField.set(buffer, intCompact)
            _scaleField.set(buffer, scale.toByte())
            objectMap[propertyIndex] = null
        } else {
            _intCompactField.set(buffer, INFLATED)
            _scaleField.set(buffer, 0)
            objectMap[propertyIndex] = value
        }
    }

    private companion object {
        private val UNSAFE: Unsafe
        private val INT_COMPACT_OFFSET: Long
        private const val INFLATED = Long.MIN_VALUE

        init {
            try {
                val unsafeField = Unsafe::class.java.getDeclaredField("theUnsafe")
                unsafeField.setAccessible(true)
                UNSAFE = unsafeField.get(null) as Unsafe
                INT_COMPACT_OFFSET = UNSAFE.objectFieldOffset(BigDecimal::class.java.getDeclaredField("intCompact"))
            } catch (e: java.lang.Exception) {
                throw java.lang.RuntimeException("Failed to initialize Unsafe fields", e)
            }
        }
    }
}
//endregion

//region =================== LayoutManager ========================
internal object LayoutManager {
    fun assignOffsets(fields: List<Field>) {
        var offsetCounter = 0
        var bitIndex = 0

        for (field in fields.sortedByDescending { it.alignment }) {
            when (field) {
                is BooleanField -> {
                    field.setOffset(offsetCounter, bitIndex++)
                    if (bitIndex == 7) {
                        // 如果当前位是最后一个位（即 bitIndex 为 7），则需要跳到下一个字节
                        offsetCounter++
                        bitIndex = 0
                    }
                }
                else -> {
                    val alignedOffset = alignUp(offsetCounter, field.alignment)
                    field.setOffset(alignedOffset, 0)
                    offsetCounter = alignedOffset + field.size
                }
            }
        }
    }

    private fun alignUp(offset: Int, align: Int): Int = (offset + align - 1) and (-align)
}
//endregion