package org.example

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