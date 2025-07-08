package com.example.orm

class ArrayTuple(size : Int) : AbstractTuple() {
    private val values = arrayOfNulls<Any>(size)

    override fun getItem(index: Int): Any? {
        return values[index]
    }

    override fun setItem(index: Int, value: Any?) {
        values[index] = value
    }

    override val directSize: Int
        get() = values.size

    override fun getFieldType(index: Int): Class<*> {
        val value = values[index]
        return value?.javaClass ?: Any::class.java
    }

    override fun getInt(index: Int): Int {
        val value = values[index]
        return value?.let { it as Int } ?: 0
    }

    override fun setInt(index: Int, value: Int) {
        values[index] = value
    }
}