package org.example

import java.nio.ByteBuffer

//region ================= fields =======================
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

//region =================== PropertyAccessor ========================
internal abstract class PropertyAccessor {
    abstract val nullable: Boolean
    abstract val defaultValue : Any?
    abstract fun getFields(): List<Field>
    abstract fun get(buffer: ByteBuffer): Any?
    abstract fun set(buffer: ByteBuffer, value: Any?)
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

    private fun alignUp(offset: Int, align: Int): Int = (offset + align - 1) and (align.inv())
}
//endregion