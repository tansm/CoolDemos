package com.example.orm

/**
 * TupleExceptions: 包含静态辅助方法，用于统一处理元组操作中的异常。
 * 旨在减少动态生成元组类中的重复字节码。
 */
object TupleUtils {

    // 定义 ValueTuple 的最大直接字段数
    const val MAX_DIRECT_FIELDS = 7

    @JvmStatic
    fun <R> throwIndexOutOfBounds(index: Int) : R {
        throw IndexOutOfBoundsException("Index $index out of bounds.")
    }

    @JvmStatic
    fun throwClassCastException(index: Int, expectedType: String) {
        throw ClassCastException("Value at index $index is not a $expectedType.")
    }

    @JvmStatic
    fun throwIllegalStateException(index: Int) {
        throw IllegalStateException("Rest field is null but index $index requires it.")
    }
}