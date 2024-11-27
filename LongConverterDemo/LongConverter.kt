fun main() {
    performanceTest()
}

private fun getDimensionInfo(size : Int) : Array<out IntArray>{
    return when(size){
        1 ->{
            arrayOf(
                intArrayOf(900),
                intArrayOf(0,1,9,5),    //min = 0, max: 9
                intArrayOf(900),
                intArrayOf(900),
                intArrayOf(900),

                intArrayOf(900),
                intArrayOf(900),
                intArrayOf(900),
                intArrayOf(900),
                intArrayOf(900),

                intArrayOf(900),
            )
        }
        2 ->{
            arrayOf(
                intArrayOf(900),
                intArrayOf(0,1,9,5),    //min = 0, max: 9
                intArrayOf(900),
                intArrayOf(900,832),    //min = 832,max = 900
                intArrayOf(900),

                intArrayOf(900),
                intArrayOf(900),
                intArrayOf(900),
                intArrayOf(900),
                intArrayOf(900),

                intArrayOf(900),
            )
        }
        3 ->{
            arrayOf(
                intArrayOf(900),
                intArrayOf(0,1,9,5),    //min = 0, max: 9
                intArrayOf(900),
                intArrayOf(900,832),    //min = 832,max = 900
                intArrayOf(3,6,8,4,2,2,44,56,67,32,123), //min=2, max=123

                intArrayOf(900),
                intArrayOf(900),
                intArrayOf(900),
                intArrayOf(900),
                intArrayOf(900),

                intArrayOf(900),
            )
        }
        4 ->{
            arrayOf(
                intArrayOf(900),
                intArrayOf(0,1,9,5),    //min = 0, max: 9
                intArrayOf(900),
                intArrayOf(900,832),    //min = 832,max = 900
                intArrayOf(3,6,8,4,2,2,44,56,67,32,123), //min=2, max=123

                intArrayOf(900),
                intArrayOf(900),
                intArrayOf(1000_0000,1000_0002,1000_0001), //min=1000_0000, max=1000_0002
                intArrayOf(900),
                intArrayOf(900),

                intArrayOf(900),
            )
        }
        5 ->{
            arrayOf(
                intArrayOf(900),
                intArrayOf(0,1,9,5),    //min = 0, max: 9
                intArrayOf(900),
                intArrayOf(900,832),    //min = 832,max = 900
                intArrayOf(3,6,8,4,2,2,44,56,67,32,123), //min=2, max=123

                intArrayOf(900),
                intArrayOf(900),
                intArrayOf(1000_0000,1000_0002,1000_0001), //min=1000_0000, max=1000_0002
                intArrayOf(900),
                intArrayOf(4,9,10),     //min=4, max =10

                intArrayOf(900),
            )
        }

        6 ->{
            arrayOf(
                intArrayOf(900),
                intArrayOf(0,1,9,5),    //min = 0, max: 9
                intArrayOf(900),
                intArrayOf(900,832),    //min = 832,max = 900
                intArrayOf(3,6,8,4,2,2,44,56,67,32,123), //min=2, max=123

                intArrayOf(900),
                intArrayOf(900),
                intArrayOf(1000_0000,1000_0002,1000_0001), //min=1000_0000, max=1000_0002
                intArrayOf(900),
                intArrayOf(4,9,10),     //min=4, max =10

                intArrayOf(9,4),        //min=4, max = 9
            )
        }

        7 ->{
            arrayOf(
                intArrayOf(8,7),        //min = 7,max= 8
                intArrayOf(0,1,9,5),    //min = 0, max: 9
                intArrayOf(900),
                intArrayOf(900,832),    //min = 832,max = 900
                intArrayOf(3,6,8,4,2,2,44,56,67,32,123), //min=2, max=123

                intArrayOf(900),
                intArrayOf(900),
                intArrayOf(1000_0000,1000_0002,1000_0001), //min=1000_0000, max=1000_0002
                intArrayOf(900),
                intArrayOf(4,9,10),     //min=4, max =10

                intArrayOf(9,4),        //min=4, max = 9
            )
        }
        else -> throw RuntimeException()
    }
}

fun performanceTest(name: String, target: LongConverter) {
    val key = intArrayOf(9, 5, 900, 832, 67, 800, 100_0000, 1000_0001, 3, 4, 9)
    var x = 0L
    measureTime {
        for (i in 0 until Int.MAX_VALUE) {
            key[0] = i
            x += target.toX(key)
        }
    }.inWholeMilliseconds.also {
        println("$name : $it ms.  result = $x")
    }
}

fun performanceTest(){
    for (i in 1 until 8){
        performanceTest("basic$i", LongConverter(getDimensionInfo(i)))
    }
}


typealias int = Int
typealias long = Long

/**
 * IConverter 对于 long 类型的特定接口，避免java的装箱行为。
 */
interface ILongConverter<Y>{
    fun toX(y : Y) : long

    /**
     * 最大的可能值，不包括此值。
     */
    val max : long
}

/**
 * 将一个维度组合，转为为 矢量 方向的一个值。
 * 例如一个存在维度 A、B、C,其中可能出现的成员是 A[4,7,9], B[1,5], C[100]，这是通过 dimensionInfo 传入的信息。
 * 通过分析，我们发现维度 A，最大值 9，最小值 4， 仅仅需要 (9-4) 5 作为笛卡尔运算的步进长度，
 * 同理，B维度为 （5-1） 4， carry 为 （5 * 4）20,
 * 而C只有一个成员，不需要参与笛卡尔运算。
 *
 * 所以一个维度组合 [A:7, B:5, C:100] 映射到 一个 long 的值为：
 * (7-4) * 5 + （5-1）*20
 * = 15 + 80
 * = 95
 */
class LongConverter(
    dimensionInfo: Array<out IntArray>
)  {
    //如果某个维度的成员只有一种可能，此变量的此维度位置将存放这个值。
    private val _fixedMembers = IntArray(dimensionInfo.size)
    //如果维度的成员存在多种可能，那么存储在这个映射表。
    private val _mappings : Array<MemberMap>

    init {
        val list = mutableListOf<MemberMap>()
        var carry = 1L //乘法的因子

        for((i,dim) in dimensionInfo.withIndex()){
            when(dim.size){
                0 -> throw RuntimeException("0")
                //如果此维度仅仅一个可用成员，将跳过映射，例如在条件中，很多的维度被限制在一个成员中。
                1 -> _fixedMembers[i] = dim.first()
                //
                else ->{
                    val map = MemberMap(i,dim,carry)
                    if(map.step == 0) { //虽然给了多个，但是都是一样的值。
                        _fixedMembers[i] = map.minValue
                    }else {
                        carry *= map.step.toLong()
                        list.add(map)
                    }
                }
            }
        }
        _mappings = list.toTypedArray()
    }

    /**
     * 根据一个给定的维度组合找到所有可能的组合中按照某个确定顺序排列后的位置
     */
    fun toX(y: IntArray): long {
        var result = 0L
        for (mapping in _mappings) {
            //从有效维度中获取成员的值，并减去最小值，作为实际运算的值
            result += mapping.toX(y)
        }

        return result
    }

    val max: long
        get() = TODO("Not yet implemented")

    internal class MemberMap(
        val dimensionPosition : int,
        members : IntArray,
        val carry : long
    ){
        /** 实际成员的最小值，在上面的例子中，值为 199  */
        val minValue : int = members.minOrNull() ?: 0

        /** 实际成员的最大值，在上面的例子中，值为 209 */
        private val maxValue : int = members.maxOrNull() ?: 0

        /** 成员从最小值到最大值的步长，在上面的例子中，值为 (209-199) = 10 */
        val step : int get() = maxValue - minValue + 1

        fun toX(y : IntArray) = carry * (y[dimensionPosition] - minValue)

        override fun toString(): String {
            return "{pos:$dimensionPosition, min:$minValue, max:$maxValue, step:$step, carry:$carry}"
        }
    }

    /*fun createSimdVersion() : ILongConverter<IntArray> {
        return LongConverterSimd.create(_mappings,this)
    }*/
}