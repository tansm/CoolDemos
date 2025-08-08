package org.example

import sun.misc.Unsafe
import java.math.BigDecimal
import java.util.UUID
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Date
import kotlin.math.max
import kotlin.math.min

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

    protected companion object{
        val UNSAFE = try {
            val unsafeField = Unsafe::class.java.getDeclaredField("theUnsafe")
            unsafeField.setAccessible(true)
            unsafeField.get(null) as Unsafe
        } catch (e: java.lang.Exception) {
            throw java.lang.RuntimeException("Failed to initialize Unsafe fields", e)
        }

        val BASE_OFFSET : Long = Unsafe.ARRAY_BYTE_BASE_OFFSET.toLong()
    }
}

internal class BooleanField : Field() {
    fun get(buffer: ByteArray): Boolean = ((buffer[byteOffset].toInt() shr bitIndex) and 1) == 1

    fun set(buffer: ByteArray, value: Boolean) {
        val original = buffer[byteOffset].toInt()
        val updated = if (value) original or (1 shl bitIndex) else original and (1 shl bitIndex).inv()
        buffer[byteOffset] = updated.toByte()
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
    fun get(buffer: ByteArray): Byte = buffer[offset]
    fun set(buffer: ByteArray, value: Byte) { buffer[offset] = value }
    override val size get() = Byte.SIZE_BYTES
    override val alignment get() = 1
}

internal class ShortField : Field() {
    fun get(buffer: ByteArray): Short = UNSAFE.getShort(buffer, BASE_OFFSET + offset)
    fun set(buffer: ByteArray, value: Short) { UNSAFE.putShort(buffer, BASE_OFFSET + offset, value) }
    override val size get() = Short.SIZE_BYTES
    override val alignment get() = 2
}

internal class IntField : Field() {
    fun get(buffer: ByteArray): Int = UNSAFE.getInt(buffer, BASE_OFFSET + offset)
    fun set(buffer: ByteArray, value: Int) { UNSAFE.putInt(buffer, BASE_OFFSET + offset, value) }
    override val size get() = Int.SIZE_BYTES
    override val alignment get() = 4
}

internal class LongField : Field() {
    fun get(buffer: ByteArray): Long = UNSAFE.getLong(buffer, BASE_OFFSET + offset)
    fun set(buffer: ByteArray, value: Long) { UNSAFE.putLong(buffer, BASE_OFFSET + offset, value) }
    override val size get() = Long.SIZE_BYTES
    override val alignment get() = 8
}

internal class FloatField : Field() {
    fun get(buffer: ByteArray): Float = UNSAFE.getFloat(buffer, BASE_OFFSET + offset)
    fun set(buffer: ByteArray, value: Float) { UNSAFE.putFloat(buffer, BASE_OFFSET + offset, value) }
    override val size get() = Float.SIZE_BYTES
    override val alignment get() = 4
}

internal class DoubleField : Field() {
    fun get(buffer: ByteArray): Double = UNSAFE.getDouble(buffer, BASE_OFFSET + offset)
    fun set(buffer: ByteArray, value: Double) { UNSAFE.putDouble(buffer, BASE_OFFSET + offset, value) }
    override val size get() = Double.SIZE_BYTES
    override val alignment get() = 8
}

//endregion

//region =================== SparseObjectMap ========================
internal class ByteDataStorage private constructor(
    val buffer: ByteArray, private var objectMap: Array<Any?>, private val maxObjectSize : Int
) {
    constructor(byteSize: Int, objectSize : Int, maxObjectSize : Int)
            : this(ByteArray(byteSize), arrayOfNulls(objectSize), maxObjectSize)

    fun getLocalValue(accessor: PropertyAccessor): Any? {
        return accessor.get(this)
    }

    fun setLocalValue(accessor: PropertyAccessor, value: Any?) {
        accessor.set(this, value)
    }

    fun memberClone(): ByteDataStorage {
        return ByteDataStorage(buffer.copyOf(), objectMap.copyOf(), maxObjectSize)
    }

    // region Object 的优化存储
    // objectMap 最初的版本是使用 稀疏字典 SparseObjectMap，后来发现如果 string 字段太多反而效果不佳。
    // 后来改为 Array，大小是所有可能用到 Object 存储的属性的总和，但是 BigDecimal 也很尴尬，一般情况下，是不会存储到 Array，
    // 提前分配空间太浪费，所以这里封装一下，保证正常情况下没有分配空间，但是 真要用的时候也可以重新分配。
    fun getObject(key: Int): Any? {
        if(key < 0){
            throw IllegalArgumentException("key must be >= 0")
        }
        if(key in objectMap.indices){
            return objectMap[key]
        }
        return null // 不要自动扩容。
    }

    fun setObject(key: Int, value: Any?) {
        if(key < 0){
            throw IllegalArgumentException("key must be >= 0")
        }
        if(key in objectMap.indices){
            objectMap[key] = value
        } else {
            // 设置的为null，无需扩容，语义可以保持不变
            if(value == null){
                return
            }

            if(key < maxObjectSize){
                ensureCapacity(key + 1)
                objectMap[key] = value
            } else {
                throw IllegalStateException("key($key) must be >= 0 and < $maxObjectSize")
            }
        }
    }

    private fun ensureCapacity(cap: Int) {
        // 这里没有使用 * 2 的常见策略，因为实体中 Object 类型的字段已经初始化过，再 * 2 就会激增太多。
        // 目前是 16， 一个 object 如果压缩指针是 4 字节，那么就是 64 Byte.
        val newCap = min(max(objectMap.size + 16, cap),maxObjectSize)
        if(newCap > objectMap.size){
            objectMap = objectMap.copyOf(newCap)
        }
    }
    //endregion
}
//endregion

//region =================== PropertyAccessor ========================
internal abstract class PropertyAccessor {
    /**
     * 用于标记该属性需要 objectMap 存储的概率, 默认是 0.0f 表示绝对不会使用 Object 存储。
     * 只有返回 > 0 的才会被分配 objectIndex，比如 BigDecimal 概率极低，但仍然大于0，会被考虑分配 ObjectId
     */
    open val requiresObjectStorage: Float get() = 0.0f

    private var _objectIndex : Int = -1
    val objectIndex: Int get() = _objectIndex
    fun resetObjectIndex(index: Int){
        _objectIndex = index
    }

    abstract val nullable: Boolean
    abstract val defaultValue : Any?
    abstract fun getFields(): List<Field>
    abstract fun get(buffer: ByteArray): Any?
    open fun get(storage: ByteDataStorage) : Any?{
        return get(storage.buffer)
    }
    abstract fun set(buffer: ByteArray, value: Any?)
    open fun set(storage: ByteDataStorage, value: Any?){
        set(storage.buffer, value)
    }
}

internal class ObjectPropertyAccessor : PropertyAccessor(){
    override fun getFields(): List<Field> = emptyList()
    override val defaultValue: Any? get() = null
    override val nullable: Boolean get() = true
    override val requiresObjectStorage: Float get() = 1.0f

    override fun get(storage: ByteDataStorage): Any? {
        return storage.getObject(this.objectIndex)
    }

    override fun set(storage: ByteDataStorage, value: Any?) {
        storage.setObject(this.objectIndex, value)
    }

    override fun get(buffer: ByteArray): Any? {
        throw RuntimeException("not support")
    }

    override fun set(buffer: ByteArray, value: Any?) {
        throw RuntimeException("not support")
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

    override fun get(buffer: ByteArray): Any? {
        if (_definedField?.get(buffer) == false) return defaultValue
        return _valueField.get(buffer)
    }

    override fun set(buffer: ByteArray, value: Any?) {
        _definedField?.set(buffer, value != null)
        if (value == null) _valueField.set(buffer, _defaultValue) else _valueField.set(buffer, value as Boolean)
    }

    fun getBoolean(buffer: ByteArray): Boolean {
        if (_definedField?.get(buffer) == false) return _defaultValue
        return _valueField.get(buffer)
    }

    fun setBoolean(buffer: ByteArray, value: Boolean) {
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

    override fun get(buffer: ByteArray): Any? {
        if (_definedField?.get(buffer) == false) return defaultValue
        return _valueField.get(buffer)
    }

    override fun set(buffer: ByteArray, value: Any?) {
        _definedField?.set(buffer, value != null)
        if (value == null) _valueField.set(buffer, _defaultValue) else _valueField.set(buffer, value as Byte)
    }

    fun getByte(buffer: ByteArray): Byte {
        if (_definedField?.get(buffer) == false) return _defaultValue
        return _valueField.get(buffer)
    }

    fun setByte(buffer: ByteArray, value: Byte) {
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

    override fun get(buffer: ByteArray): Any? {
        if (_definedField?.get(buffer) == false) return defaultValue
        return _valueField.get(buffer)
    }

    override fun set(buffer: ByteArray, value: Any?) {
        _definedField?.set(buffer, value != null)
        if (value == null) _valueField.set(buffer, _defaultValue) else _valueField.set(buffer, value as Short)
    }

    fun getShort(buffer: ByteArray): Short {
        if (_definedField?.get(buffer) == false) return _defaultValue
        return _valueField.get(buffer)
    }

    fun setShort(buffer: ByteArray, value: Short) {
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

    override fun get(buffer: ByteArray): Any? {
        if (_definedField?.get(buffer) == false) return defaultValue
        return _valueField.get(buffer)
    }

    override fun set(buffer: ByteArray, value: Any?) {
        _definedField?.set(buffer, value != null)
        if (value == null) _valueField.set(buffer, _defaultValue) else _valueField.set(buffer, value as Int)
    }

    fun getInt(buffer: ByteArray): Int {
        if (_definedField?.get(buffer) == false) return _defaultValue
        return _valueField.get(buffer)
    }

    fun setInt(buffer: ByteArray, value: Int) {
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

    override fun get(buffer: ByteArray): Any? {
        if (_definedField?.get(buffer) == false) return defaultValue
        return _valueField.get(buffer)
    }

    override fun set(buffer: ByteArray, value: Any?) {
        _definedField?.set(buffer, value != null)
        if (value == null) _valueField.set(buffer, _defaultValue) else _valueField.set(buffer, value as Long)
    }

    fun getLong(buffer: ByteArray): Long {
        if (_definedField?.get(buffer) == false) return _defaultValue
        return _valueField.get(buffer)
    }

    fun setLong(buffer: ByteArray, value: Long) {
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

    override fun get(buffer: ByteArray): Any? {
        if (_definedField?.get(buffer) == false) return defaultValue
        return _valueField.get(buffer)
    }

    override fun set(buffer: ByteArray, value: Any?) {
        _definedField?.set(buffer, value != null)
        if (value == null) _valueField.set(buffer, _defaultValue) else _valueField.set(buffer, value as Float)
    }

    fun getFloat(buffer: ByteArray): Float {
        if (_definedField?.get(buffer) == false) return _defaultValue
        return _valueField.get(buffer)
    }

    fun setFloat(buffer: ByteArray, value: Float) {
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

    override fun get(buffer: ByteArray): Any? {
        if (_definedField?.get(buffer) == false) return defaultValue
        return _valueField.get(buffer)
    }

    override fun set(buffer: ByteArray, value: Any?) {
        _definedField?.set(buffer, value != null)
        if (value == null) _valueField.set(buffer, _defaultValue) else _valueField.set(buffer, value as Double)
    }

    fun getDouble(buffer: ByteArray): Double {
        if (_definedField?.get(buffer) == false) return _defaultValue
        return _valueField.get(buffer)
    }

    fun setDouble(buffer: ByteArray, value: Double) {
        _valueField.set(buffer, value)
        _definedField?.set(buffer, true)
    }
}

internal class BigDecimalPropertyAccessor(override val nullable: Boolean) : PropertyAccessor() {
    private val _intCompactField = LongField()
    private val _scaleField = ByteField()
    private val _definedField: BooleanField? = if (nullable) BooleanField() else null

    override fun getFields() = listOfNotNull(_intCompactField, _scaleField, _definedField)
    override val requiresObjectStorage: Float get() = 0.001f  // 概率极低，但仍然需要分配 ObjectId

    // 为降低复杂度，BigDecimal 不支持自定义的缺省值。 nullable = false 时，intCompact 和 scale 正好都是 0.
    override val defaultValue: Any? get() = defaultBigDecimalValue
    private val defaultBigDecimalValue : BigDecimal? get() = if (nullable) null else BigDecimal.ZERO

    override fun get(buffer: ByteArray): Any? {
        throw RuntimeException("not support")
    }

    override fun get(storage: ByteDataStorage): Any? {
        return getBigDecimal(storage)
    }

    override fun set(buffer: ByteArray, value: Any?) {
        throw RuntimeException("not support")
    }

    override fun set(storage: ByteDataStorage, value: Any?) {
        setBigDecimal(storage, value as BigDecimal?)
    }

    fun getBigDecimal(storage: ByteDataStorage): BigDecimal? {
        val buffer = storage.buffer

        if (_definedField?.get(buffer) == false) return null

        val intCompact = _intCompactField.get(buffer)
        return if (intCompact == INFLATED) {
            // 从 objectMap 中获取值，如果获取的值是 null, 返回 默认值
            // 注意这里和 UUID 的处理方式不同，UUID 一定能用两个 long 存储，但 BigDecimal 可能数值过大，还是放到 objectMap
            // 这里 obj 一定不会为 null(程序实现为了简单返回了 defaultBigDecimalValue )，而 UUID 简单的返回 EMPTY 即可。
            val obj: BigDecimal? = storage.getObject(objectIndex) as BigDecimal?
            obj ?: defaultBigDecimalValue
        } else {
            // 从数据中恢复, scale 可以是负数也可以是正数
            BigDecimal.valueOf(intCompact, _scaleField.get(buffer).toInt())
        }
    }

    fun setBigDecimal(storage: ByteDataStorage, value: BigDecimal?) {
        val buffer = storage.buffer

        _definedField?.set(buffer, value != null)
        if (value == null) {
            _intCompactField.set(buffer, 0L)
            _scaleField.set(buffer, 0)
            storage.setObject(objectIndex, null)
            return
        }

        val intCompact = value.getIntCompact()
        val scale = value.scale()

        if (intCompact != INFLATED && (scale in Byte.MIN_VALUE .. Byte.MAX_VALUE)) {
            _intCompactField.set(buffer, intCompact)
            _scaleField.set(buffer, scale.toByte())
            storage.setObject(objectIndex, null)
        } else {
            _intCompactField.set(buffer, INFLATED)
            _scaleField.set(buffer, 0)
            storage.setObject(objectIndex, value)
        }
    }

    private companion object {
        private val UNSAFE = try {
            val unsafeField = Unsafe::class.java.getDeclaredField("theUnsafe")
            unsafeField.setAccessible(true)
            unsafeField.get(null) as Unsafe
        } catch (_: java.lang.Exception) {
            println("Failed to initialize Unsafe fields")
            //throw java.lang.RuntimeException("Failed to initialize Unsafe fields", e)
            null
        }

        private val INT_COMPACT_OFFSET: Long = if (UNSAFE != null) {
            try {
                UNSAFE.objectFieldOffset(BigDecimal::class.java.getDeclaredField("intCompact"))
            } catch (_: java.lang.Exception) {
                println("Failed to initialize intCompact field")
                -1L
            }
        } else {
            -1L
        }

        private const val INFLATED = Long.MIN_VALUE

        @JvmStatic
        private fun BigDecimal.getIntCompact(): Long {
            return if (UNSAFE == null || INT_COMPACT_OFFSET < 0L) {
                getIntCompactWithPublicAPI(this)
            } else {
                UNSAFE.getLong(this, INT_COMPACT_OFFSET)
            }
        }

        // 这种方式非常慢。
        @JvmStatic
        private fun getIntCompactWithPublicAPI(value: BigDecimal): Long {
            val unscaledValue = value.unscaledValue()
            return if (unscaledValue.bitLength() <= 63) {
                unscaledValue.longValueExact()
            } else {
                INFLATED
            }
        }
    }
}

internal class UUIDPropertyAccessor(override val nullable: Boolean) : PropertyAccessor() {
    private val _mostSigBitsField = LongField()
    private val _leastSigBitsField = LongField()
    private val _definedField: BooleanField? = if (nullable) BooleanField() else null

    override fun getFields() = listOfNotNull(_mostSigBitsField, _leastSigBitsField, _definedField)

    override val defaultValue: Any? get() = if (nullable) null else EMPTY

    override fun get(buffer: ByteArray): Any? {
        return getUUID(buffer)
    }

    override fun set(buffer: ByteArray, value: Any?) {
        setUUID(buffer, value as UUID?)
    }

    fun getUUID(buffer: ByteArray): UUID? {
        if (_definedField?.get(buffer) == false) return null
        val most = _mostSigBitsField.get(buffer)
        val least = _leastSigBitsField.get(buffer)
        return if (most == 0L && least == 0L) EMPTY else UUID(most, least)
    }

    fun setUUID(buffer: ByteArray, value: UUID?) {
        _definedField?.set(buffer, value != null)
        if (value == null) {
            _mostSigBitsField.set(buffer, 0L)
            _leastSigBitsField.set(buffer, 0L)
        } else {
            _mostSigBitsField.set(buffer, value.mostSignificantBits)
            _leastSigBitsField.set(buffer, value.leastSignificantBits)
        }
    }

    private companion object {
        val EMPTY = UUID(0L, 0L)
    }
}

internal class LocalDatePropertyAccessor(override val nullable: Boolean) : PropertyAccessor() {
    private val _epochDayField = LongField()
    private val _definedField: BooleanField? = if (nullable) BooleanField() else null

    override fun getFields() = listOfNotNull(_epochDayField, _definedField)
    override val defaultValue: Any? get() = if (nullable) null else EMPTY

    override fun get(buffer: ByteArray): Any? = getLocalDate(buffer)
    override fun set(buffer: ByteArray, value: Any?) = setLocalDate(buffer, value as LocalDate?)

    fun getLocalDate(buffer: ByteArray): LocalDate? {
        if (_definedField?.get(buffer) == false) return null
        val epochDay = _epochDayField.get(buffer)
        return if (epochDay == 0L) EMPTY else LocalDate.ofEpochDay(epochDay)
    }

    fun setLocalDate(buffer: ByteArray, value: LocalDate?) {
        _definedField?.set(buffer, value != null)
        if (value == null) {
            _epochDayField.set(buffer, 0L)
        } else {
            _epochDayField.set(buffer, value.toEpochDay())
        }
    }

    companion object {
        val EMPTY: LocalDate = LocalDate.ofEpochDay(0)
    }
}

internal class LocalTimePropertyAccessor(override val nullable: Boolean) : PropertyAccessor() {
    private val _nanoOfDayField = LongField()
    private val _definedField: BooleanField? = if (nullable) BooleanField() else null

    override fun getFields() = listOfNotNull(_nanoOfDayField, _definedField)
    override val defaultValue: Any? get() = if (nullable) null else EMPTY

    override fun get(buffer: ByteArray): Any? = getLocalTime(buffer)
    override fun set(buffer: ByteArray, value: Any?) = setLocalTime(buffer, value as LocalTime?)

    fun getLocalTime(buffer: ByteArray): LocalTime? {
        if (_definedField?.get(buffer) == false) return null
        val nanoOfDay = _nanoOfDayField.get(buffer)
        return if (nanoOfDay == 0L) EMPTY else LocalTime.ofNanoOfDay(nanoOfDay)
    }

    fun setLocalTime(buffer: ByteArray, value: LocalTime?) {
        _definedField?.set(buffer, value != null)
        if (value == null) {
            _nanoOfDayField.set(buffer, 0L)
        } else {
            _nanoOfDayField.set(buffer, value.toNanoOfDay())
        }
    }

    companion object {
        val EMPTY: LocalTime = LocalTime.ofNanoOfDay(0)
    }
}

internal class LocalDateTimePropertyAccessor(override val nullable: Boolean) : PropertyAccessor() {
    private val _epochSecondField = LongField()
    private val _nanoField = IntField()
    private val _definedField: BooleanField? = if (nullable) BooleanField() else null

    override fun getFields() = listOfNotNull(_epochSecondField, _nanoField, _definedField)
    override val defaultValue: Any? get() = if (nullable) null else EMPTY

    override fun get(buffer: ByteArray): Any? = getLocalDateTime(buffer)
    override fun set(buffer: ByteArray, value: Any?) = setLocalDateTime(buffer, value as LocalDateTime?)

    fun getLocalDateTime(buffer: ByteArray): LocalDateTime? {
        if (_definedField?.get(buffer) == false) return null
        val epochSecond = _epochSecondField.get(buffer)
        val nano = _nanoField.get(buffer)
        return if (epochSecond == 0L && nano == 0) EMPTY else LocalDateTime.ofEpochSecond(epochSecond, nano, ZoneOffset.UTC)
    }

    fun setLocalDateTime(buffer: ByteArray, value: LocalDateTime?) {
        _definedField?.set(buffer, value != null)
        if (value == null) {
            _epochSecondField.set(buffer, 0L)
            _nanoField.set(buffer, 0)
        } else {
            _epochSecondField.set(buffer, value.toEpochSecond(ZoneOffset.UTC))
            _nanoField.set(buffer, value.nano)
        }
    }

    companion object {
        val EMPTY: LocalDateTime = LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC)
    }
}

internal class InstantPropertyAccessor(override val nullable: Boolean) : PropertyAccessor() {
    private val _epochSecondField = LongField()
    private val _nanoField = IntField()
    private val _definedField: BooleanField? = if (nullable) BooleanField() else null

    override fun getFields() = listOfNotNull(_epochSecondField, _nanoField, _definedField)
    override val defaultValue: Any? get() = if (nullable) null else Instant.EPOCH

    override fun get(buffer: ByteArray): Any? = getInstant(buffer)
    override fun set(buffer: ByteArray, value: Any?) = setInstant(buffer, value as Instant?)

    fun getInstant(buffer: ByteArray): Instant? {
        if (_definedField?.get(buffer) == false) return null
        val epochSecond = _epochSecondField.get(buffer)
        val nano = _nanoField.get(buffer)
        return if (epochSecond == 0L && nano == 0) Instant.EPOCH else Instant.ofEpochSecond(epochSecond, nano.toLong())
    }

    fun setInstant(buffer: ByteArray, value: Instant?) {
        _definedField?.set(buffer, value != null)
        if (value == null) {
            _epochSecondField.set(buffer, 0L)
            _nanoField.set(buffer, 0)
        } else {
            _epochSecondField.set(buffer, value.epochSecond)
            _nanoField.set(buffer, value.nano)
        }
    }
}

internal class DatePropertyAccessor(override val nullable: Boolean) : PropertyAccessor() {
    private val _timeField = LongField()
    private val _definedField: BooleanField? = if (nullable) BooleanField() else null

    override fun getFields() = listOfNotNull(_timeField, _definedField)
    override val defaultValue: Any? get() = if (nullable) null else EMPTY

    override fun get(buffer: ByteArray): Any? = getDate(buffer)
    override fun set(buffer: ByteArray, value: Any?) = setDate(buffer, value as Date?)

    fun getDate(buffer: ByteArray): Date? {
        if (_definedField?.get(buffer) == false) return null
        val time = _timeField.get(buffer)
        return if (time == 0L) EMPTY else Date(time)
    }

    fun setDate(buffer: ByteArray, value: Date?) {
        _definedField?.set(buffer, value != null)
        if (value == null) {
            _timeField.set(buffer, 0L)
        } else {
            _timeField.set(buffer, value.time)
        }
    }

    companion object {
        val EMPTY: Date = Date(0L)
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

    fun calcObjectInitSize(properties: Iterable<PropertyAccessor>): Pair<Int, Int> {
        var initSize = 0.0f
        val orderedProperties = ArrayList<PropertyAccessor>()

        for (property in properties) {
            // 只要有概率进行 Object 存储，就一定分配编号，
            val size = property.requiresObjectStorage
            if (size > 0.0f) {
                orderedProperties.add(property)
                require(size <= 1.0f)
                initSize += size
            }
        }

        // 将 properties 中 requiresObjectStorage 的属性按照权重排序，为什么要这样做呢？
        // 假设 0..9 是 BigDecimal 类型， 10..89 是 int 类型， 90..99 是 String 类型，
        // initSize.toInt() 最终只有 String 预分配了 10, 即初始化 objectMap[10],但他们的 ObjectIndex 是 10 .. 19
        // 当为任何一个 String 属性赋值时，其编号都大于 10，将触发扩容，这违背了我们最初的设计：希望常见的Object类型预先分配空间。
        // 所以这里根据 requiresObjectStorage 排序后，String 被排序到前面，即 ObjectIndex 是 0 .. 9,这时不再发生扩容。
        var objectSize = 0
        orderedProperties.sortByDescending { it.requiresObjectStorage }
        for (property in orderedProperties){
            property.resetObjectIndex(objectSize++)
        }

        // 截取尾部的小数，这样比较激进一些，比如 4.009， 我们实际分配 4
        return Pair(initSize.toInt(), objectSize)
    }

    fun calcByteSize(properties: Iterable<PropertyAccessor>): Int {
        val fields = properties.flatMap { it.getFields() }
        assignOffsets(fields)
        return fields.sumOf { it.size }
    }
}
//endregion

//region =================== DynamicObjectType ========================
// 测试实现，只是实现功能，不能作为最终实现。
internal class DynamicObjectType {
    private val _properties = mutableListOf<PropertyAccessor>()
    val properties: List<PropertyAccessor> get() = _properties

    private var _byteSize = -1
    private var _objectSize = -1
    private var _objectMaxSize = -1

    fun <T : PropertyAccessor> register(property: T) : T {
        _properties.add(property)
        return property
    }

    fun createInstance() : ByteDataStorage{
        if(_objectSize < 0 || _byteSize < 0 || _objectMaxSize < 0){
            _byteSize = LayoutManager.calcByteSize(_properties)
            val s = LayoutManager.calcObjectInitSize(_properties)
            _objectSize = s.first
            _objectMaxSize = s.second
        }

        return ByteDataStorage(_byteSize, _objectSize, _objectMaxSize)
    }
}
//endregion