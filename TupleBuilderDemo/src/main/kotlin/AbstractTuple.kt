package com.example.orm

abstract class AbstractTuple {
    // 获取指定索引的字段值 (Any? 用于所有类型，包括原始类型的装箱值)
    abstract fun getItem(index: Int): Any?

    // 默认实现是 不包含 rest 字段。
    open fun getRest(): AbstractTuple? = null

    open val hasRestField: Boolean get() = false

    // 获取元组中字段的总数 (所有链条的总数)
    val size: Int get() = directSize + (getRest()?.size ?: 0)

    // 获取元组中字段的总数 (不包括链条的其他的字段)
    abstract val directSize: Int

    // 类型安全的 getter 方法，默认实现是当前 Tuple 没有任何字段符合此类型，从而减少最终生成的方法的字节码。
    open fun getInt(index: Int): Int {
        if (index >= directSize && hasRestField) {
            val rest = getRest()
            if (rest != null) {
                return rest.getInt(index - 7)
            } else {
                throwIllegalStateException(index)
            }
        }
        return throwIndexOrCaseException(index, "Int")
    }

    open fun getLong(index: Int): Long{
        if (index >= directSize && hasRestField) {
            val rest = getRest()
            if (rest != null) {
                return rest.getLong(index - 7)
            } else {
                throwIllegalStateException(index)
            }
        }
        return throwIndexOrCaseException(index, "Long")
    }

    open fun getBoolean(index: Int): Boolean{
        if (index >= directSize && hasRestField) {
            val rest = getRest()
            if (rest != null) {
                return rest.getBoolean(index - 7)
            } else {
                throwIllegalStateException(index)
            }
        }
        return throwIndexOrCaseException(index, "Boolean")
    }

    open fun getByte(index: Int): Byte{
        if (index >= directSize && hasRestField) {
            val rest = getRest()
            if (rest != null) {
                return rest.getByte(index - 7)
            } else {
                throwIllegalStateException(index)
            }
        }
        return throwIndexOrCaseException(index, "Byte")
    }

    open fun getShort(index: Int): Short{
        if (index >= directSize && hasRestField) {
            val rest = getRest()
            if (rest != null) {
                return rest.getShort(index - 7)
            } else {
                throwIllegalStateException(index)
            }
        }
        return throwIndexOrCaseException(index, "Short")
    }

    open fun getChar(index: Int): Char{
        if (index >= directSize && hasRestField) {
            val rest = getRest()
            if (rest != null) {
                return rest.getChar(index - 7)
            } else {
                throwIllegalStateException(index)
            }
        }
        return throwIndexOrCaseException(index, "Char")
    }

    open fun getFloat(index: Int): Float{
        if (index >= directSize && hasRestField) {
            val rest = getRest()
            if (rest != null) {
                return rest.getFloat(index - 7)
            } else {
                throwIllegalStateException(index)
            }
        }
        return throwIndexOrCaseException(index, "Float")
    }

    open fun getDouble(index: Int): Double{
        if (index >= directSize && hasRestField) {
            val rest = getRest()
            if (rest != null) {
                return rest.getDouble(index - 7)
            } else {
                throwIllegalStateException(index)
            }
        }
        return throwIndexOrCaseException(index, "Double")
    }

    override fun toString(): String {
        val sb = StringBuilder(size * 16)
        var current : AbstractTuple? = this
        var endCount = 0

        while (current != null) {
            sb.append("${current.javaClass.simpleName}(")
            endCount++

            for (i in 0 until current.directSize){
                if(i > 0){
                    sb.append(", ")
                }
                sb.append("item$i=${current.getItem(i)}")
            }

            current = current.getRest()
            if(current != null){
                sb.append(", rest=")
            }
        }

        repeat(endCount){
            sb.append(")")
        }

        return sb.toString()
    }

    // 获取指定索引字段的类型 (所有链条的总数)
    abstract fun getFieldType(index: Int): Class<*>

    //region ================== 辅助函数 =========================
    protected fun <R> throwIndexOutOfBounds(index: Int): R {
        throw IndexOutOfBoundsException("Index $index out of bounds.")
    }

    protected fun <R> throwClassCastException(index: Int, expectedType: String): R {
        throw ClassCastException("Value at index $index is not a $expectedType.")
    }

    protected fun throwIllegalStateException(index: Int) {
        throw IllegalStateException("Rest field is null but index $index requires it.")
    }

    protected fun <R> throwIndexOrCaseException(index: Int, expectedType: String): R {
        return if (index in 0 until directSize) {
            throwClassCastException(index, expectedType)
        } else {
            throwIndexOutOfBounds(index)
        }
    }
    //endregion
}