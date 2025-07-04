package com.example.orm

// --- 1. 定义 ITuple 接口 ---
interface ITuple {
    // 获取指定索引的字段值 (Any? 用于所有类型，包括原始类型的装箱值)
    fun getItem(index: Int): Any?
    // 获取元组中字段的总数 (所有链条的总数)
    fun getSize(): Int

    // 类型安全的 getter 方法
    fun getInt(index: Int): Int
    fun getLong(index: Int): Long
    fun getBoolean(index: Int): Boolean
    fun getByte(index: Int): Byte
    fun getShort(index: Int): Short
    fun getChar(index: Int): Char
    fun getFloat(index: Int): Float
    fun getDouble(index: Int): Double

    // 获取指定索引字段的类型 (所有链条的总数)
    fun getFieldType(index: Int): Class<*>
}