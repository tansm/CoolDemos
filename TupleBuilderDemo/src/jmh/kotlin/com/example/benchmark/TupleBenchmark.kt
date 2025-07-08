package com.example.benchmark

import com.example.orm.AbstractTuple
import com.example.orm.ArrayTuple
import com.example.orm.TupleBuilder
import com.example.orm.TupleFactory
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import java.util.concurrent.TimeUnit

/**
 * Benchmark                          Mode  Cnt  Score   Error  Units
 * TupleBenchmark.getInt_arrayTuple   avgt   25  1.171 �� 0.004  ns/op
 * TupleBenchmark.getInt_tuple        avgt   25  0.716 �� 0.002  ns/op
 * TupleBenchmark.getItem_arrayTuple  avgt   25  0.851 �� 0.002  ns/op
 * TupleBenchmark.getItem_tuple       avgt   25  0.716 �� 0.002  ns/op
 * TupleBenchmark.setInt_arrayTuple   avgt   25  1.668 �� 0.003  ns/op
 * TupleBenchmark.setInt_tuple        avgt   25  0.714 �� 0.002  ns/op
 * TupleBenchmark.setItem_arrayTuple  avgt   25  1.190 �� 0.003  ns/op
 * TupleBenchmark.setItem_tuple       avgt   25  1.224 �� 0.004  ns/op
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class TupleBenchmark {
    private lateinit var tuple: AbstractTuple
    private lateinit var arrayTuple: ArrayTuple

    @Setup
    fun setup() {
        // 假设你有 TupleFactory 和 ArrayTuple 的实现
        val types = listOf(Int::class.java, String::class.java, Boolean::class.java)
        tuple = TupleFactory(TupleBuilder()).getOrCreateTuple(types)
        arrayTuple = ArrayTuple(3)
        arrayTuple.setItem(0, 42)
        arrayTuple.setItem(1, "def")
        arrayTuple.setItem(2, true)
    }

    @Benchmark
    fun getItem_tuple(): Any? = tuple.getItem(1)

    @Benchmark
    fun setItem_tuple() { tuple.setItem(1, "def") }

    @Benchmark
    fun getInt_tuple(): Int = tuple.getInt(0)

    @Benchmark
    fun setInt_tuple() { tuple.setInt(0, 42) }

    @Benchmark
    fun getItem_arrayTuple(): Any? = arrayTuple.getItem(1)

    @Benchmark
    fun setItem_arrayTuple() { arrayTuple.setItem(1, "def") }

    @Benchmark
    fun getInt_arrayTuple(): Int = arrayTuple.getInt(0)

    @Benchmark
    fun setInt_arrayTuple() { arrayTuple.setInt(0, 42) }
}