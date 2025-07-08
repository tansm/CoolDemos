import com.example.orm.AbstractTuple
import com.example.orm.ArrayTuple
import com.example.orm.TupleBuilder
import com.example.orm.TupleFactory
import org.junit.jupiter.api.Test
import org.openjdk.jol.info.ClassLayout
import org.openjdk.jol.info.GraphLayout
import kotlin.random.Random

class TupleMemoryTest {

    private fun usedMemory(): Long {
        System.gc()
        System.gc()
        System.gc()
        System.gc()
        System.gc()
        System.gc()
        System.gc()
        System.gc()
        Thread.sleep(500)
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
    }

    /**
     * 这种利用 gc 测量内存的方法 ，行不通，结果不符合预期。
     */
    @Test
    fun compareTupleMemoryUsage() {
        val fieldCount = 100
        val objectCount = 100_000

        // 字段类型分布
        val types = List(30) { Int::class.java } +
                List(70) { Long::class.java }
                // List(40) { BigDecimal::class.java }

        // 随机数据
        val random = Random(0)
        val values : Array<Array<Any>> = Array(objectCount) {
            Array(fieldCount) { idx ->
                when {
                    idx < 30 -> random.nextInt(1_000_000_000)
                    //idx < 60 -> random.nextLong(1_000_000_000)
                    //else -> BigDecimal.valueOf(random.nextDouble(0.0, 100_000_000.0)).setScale(2, BigDecimal.ROUND_HALF_UP)
                    else -> random.nextLong(1_000_000_000)
                }
            }
        }

        val arrayTupleMem = arrayTupleTest(types, fieldCount, objectCount, values)
        val genTupleMem = genTupleTest(types, fieldCount, objectCount, values)


        println("ArrayTuple approx memory: ${arrayTupleMem / 1024} KB")
        println("Generated Tuple approx memory: ${genTupleMem / 1024} KB")
    }

    private fun genTupleTest(types : List<Class<*>>, fieldCount : Int, objectCount : Int,values: Array<Array<Any>>): Long{
        // 测试代码生成 Tuple,先编译执行一次
        TupleFactory(TupleBuilder()).getOrCreateTuple(types, fieldCount)

        System.gc(); Thread.sleep(500)
        val memBeforeGen = usedMemory()
        val tupleFactory = TupleFactory(TupleBuilder())
        val genTuples = Array(objectCount) { i ->
            val t = tupleFactory.getOrCreateTuple(types, fieldCount)
            for (j in 0 until fieldCount) t.setItem(j, values[i][j])
            t
        }
        val memAfterGen = usedMemory()
        return memAfterGen - memBeforeGen
    }

    private fun arrayTupleTest(types : List<Class<*>>, fieldCount : Int, objectCount : Int,values: Array<Array<Any>>):Long{
        // 测试 ArrayTuple
        ArrayTuple(fieldCount)

        System.gc(); Thread.sleep(500)
        val memBeforeArray = usedMemory()
        val arrayTuples = Array(objectCount) { i ->
            val t = ArrayTuple(fieldCount)
            for (j in 0 until fieldCount) t.setItem(j, values[i][j])
            t
        }
        val memAfterArray = usedMemory()
        return memAfterArray - memBeforeArray
    }

    /*
下面详细解读 TupleDemo（代码生成 Tuple）和 ArrayTuple 的内存布局对比：

---

## 1. TupleDemo（代码生成 Tuple）

- **Instance size: 696 bytes**
- 结构：
  - 对象头：12字节（8字节mark + 4字节class pointer）
  - 30个 `int` 字段：30 × 4 = 120字节
  - 70个 `long` 字段：70 × 8 = 560字节
  - 总计：12 + 120 + 560 = 692字节
  - 实际JVM对齐到8字节，最终为**696字节**
- **没有任何引用类型字段**，所有数据都直接存储在对象内部，没有额外的对象分配和引用。

- **Graph layout**：只有一个对象，大小就是 696 字节。

---

## 2. ArrayTuple

- **Instance size: 16 bytes**
  - 对象头：12字节
  - 一个 `Object[]` 引用：4字节
- **Graph layout**（包含引用对象）：
  - `Object[]` 数组本身：416字节（100个引用 × 4字节 + 数组头部）
  - 30个 `Integer` 对象：30 × 16 = 480字节
  - 70个 `Long` 对象：70 × 24 = 1680字节
  - ArrayTuple对象本身：16字节
  - **总计：2592字节**

---

## 3. 对比分析

- **TupleDemo**（代码生成 Tuple）：
  - 只占用 696 字节，所有字段都在对象内部，**没有装箱，没有额外对象分配**，空间利用率极高。
- **ArrayTuple**：
  - 虽然本体只占 16 字节，但实际数据都在 `Object[]` 和装箱对象（`Integer`、`Long`）里。
  - 总体积 2592 字节，**比 TupleDemo 大了近4倍**。

---

## 4. 结论

- **代码生成 Tuple 的空间效率远高于 ArrayTuple**，尤其是字段多、全为原始类型时。
- 这正是想要的“节省内存”效果，JOL 结果完全符合理论预期。

---

### 总结表

| 实现方式      | 实例大小 | 总对象数 | 总内存占用 | 备注                         |
|---------------|----------|----------|------------|------------------------------|
| TupleDemo     | 696 B    | 1        | 696 B      | 所有字段直接存储，无装箱     |
| ArrayTuple    | 16 B     | 102      | 2592 B     | 100字段全部装箱，分配大量对象|

---

     */
    @Test
    fun printTupleObjectLayout() {
        val types = List(30) { Int::class.java } + List(70) { Long::class.java }
        //val tuple = TupleFactory(TupleBuilder().also { it.sourceCodeOutStream = System.out }).getOrCreateTuple(types, 100)
        val tuple = TupleDemo()
        val arrayTuple = ArrayTuple(100)

        val random = Random(0)
        for (idx in 0 until 100){
            val value = when {
                idx < 30 -> random.nextInt(1_000_000_000)
                //idx < 60 -> random.nextLong(1_000_000_000)
                //else -> BigDecimal.valueOf(random.nextDouble(0.0, 100_000_000.0)).setScale(2, BigDecimal.ROUND_HALF_UP)
                else -> random.nextLong(1_000_000_000)
            }
            arrayTuple.setItem(idx, value)
            tuple.setItem(idx, value)
        }

        println("Generated Tuple object layout:")
        println(ClassLayout.parseInstance(tuple).toPrintable())
        println("Generated Tuple graph layout (including referenced objects):")
        println(GraphLayout.parseInstance(tuple).toFootprint())
        /*
TupleDemo object internals:
OFF  SZ   TYPE DESCRIPTION               VALUE
  0   8        (object header: mark)     0x0000000000000001 (non-biasable; age: 0)
  8   4        (object header: class)    0x01119340
 12   4    int TupleDemo.item0           180328214
 16   8   long TupleDemo.item30          285404198
 24   8   long TupleDemo.item31          733600624
 32   8   long TupleDemo.item32          18987777
 40   8   long TupleDemo.item33          153656395
 48   8   long TupleDemo.item34          70381881
 56   8   long TupleDemo.item35          106293997
 64   8   long TupleDemo.item36          59447443
 72   8   long TupleDemo.item37          892137233
 80   8   long TupleDemo.item38          929870545
 88   8   long TupleDemo.item39          880882532
 96   8   long TupleDemo.item40          311209903
104   8   long TupleDemo.item41          402288451
112   8   long TupleDemo.item42          818920130
120   8   long TupleDemo.item43          29333199
128   8   long TupleDemo.item44          218082331
136   8   long TupleDemo.item45          188259188
144   8   long TupleDemo.item46          194085621
152   8   long TupleDemo.item47          580314581
160   8   long TupleDemo.item48          288850906
168   8   long TupleDemo.item49          974420242
176   8   long TupleDemo.item50          610073023
184   8   long TupleDemo.item51          637091120
192   8   long TupleDemo.item52          238855868
200   8   long TupleDemo.item53          321520922
208   8   long TupleDemo.item54          573178718
216   8   long TupleDemo.item55          552975159
224   8   long TupleDemo.item56          653650666
232   8   long TupleDemo.item57          251196493
240   8   long TupleDemo.item58          457531752
248   8   long TupleDemo.item59          659291052
256   8   long TupleDemo.item60          466689708
264   8   long TupleDemo.item61          757382256
272   8   long TupleDemo.item62          557998550
280   8   long TupleDemo.item63          492045002
288   8   long TupleDemo.item64          456933701
296   8   long TupleDemo.item65          717934362
304   8   long TupleDemo.item66          128148146
312   8   long TupleDemo.item67          835209901
320   8   long TupleDemo.item68          805933458
328   8   long TupleDemo.item69          28253011
336   8   long TupleDemo.item70          265636151
344   8   long TupleDemo.item71          28153031
352   8   long TupleDemo.item72          903996340
360   8   long TupleDemo.item73          870817399
368   8   long TupleDemo.item74          736585262
376   8   long TupleDemo.item75          359062282
384   8   long TupleDemo.item76          300110954
392   8   long TupleDemo.item77          771651564
400   8   long TupleDemo.item78          566075654
408   8   long TupleDemo.item79          968535715
416   8   long TupleDemo.item80          847454780
424   8   long TupleDemo.item81          175019190
432   8   long TupleDemo.item82          829035241
440   8   long TupleDemo.item83          240817022
448   8   long TupleDemo.item84          878739061
456   8   long TupleDemo.item85          288995125
464   8   long TupleDemo.item86          377741673
472   8   long TupleDemo.item87          292590654
480   8   long TupleDemo.item88          359899850
488   8   long TupleDemo.item89          176709399
496   8   long TupleDemo.item90          428868219
504   8   long TupleDemo.item91          496427131
512   8   long TupleDemo.item92          338641872
520   8   long TupleDemo.item93          36542392
528   8   long TupleDemo.item94          442740607
536   8   long TupleDemo.item95          117050435
544   8   long TupleDemo.item96          883569297
552   8   long TupleDemo.item97          48018557
560   8   long TupleDemo.item98          902506422
568   8   long TupleDemo.item99          470335121
576   4    int TupleDemo.item1           704599848
580   4    int TupleDemo.item2           822903257
584   4    int TupleDemo.item3           420244367
588   4    int TupleDemo.item4           415412882
592   4    int TupleDemo.item5           400965107
596   4    int TupleDemo.item6           17153961
600   4    int TupleDemo.item7           405828727
604   4    int TupleDemo.item8           697865814
608   4    int TupleDemo.item9           853543459
612   4    int TupleDemo.item10          21713888
616   4    int TupleDemo.item11          134861564
620   4    int TupleDemo.item12          147991908
624   4    int TupleDemo.item13          502768462
628   4    int TupleDemo.item14          485368663
632   4    int TupleDemo.item15          784596868
636   4    int TupleDemo.item16          103896983
640   4    int TupleDemo.item17          3445945
644   4    int TupleDemo.item18          314415156
648   4    int TupleDemo.item19          924871339
652   4    int TupleDemo.item20          210727179
656   4    int TupleDemo.item21          969831518
660   4    int TupleDemo.item22          362419484
664   4    int TupleDemo.item23          34913326
668   4    int TupleDemo.item24          119660363
672   4    int TupleDemo.item25          669420251
676   4    int TupleDemo.item26          343554372
680   4    int TupleDemo.item27          391710927
684   4    int TupleDemo.item28          681012125
688   4    int TupleDemo.item29          762872744
692   4        (object alignment gap)
Instance size: 696 bytes
Space losses: 0 bytes internal + 4 bytes external = 4 bytes total

Generated Tuple graph layout (including referenced objects):
TupleDemo@57cf54e1d footprint:
     COUNT       AVG       SUM   DESCRIPTION
         1       696       696   TupleDemo
         1                 696   (total)
         */

        println("ArrayTuple object layout:")
        println(ClassLayout.parseInstance(arrayTuple).toPrintable())
        println("ArrayTuple graph layout (including referenced objects):")
        println(GraphLayout.parseInstance(arrayTuple).toFootprint())

        /*

ArrayTuple object layout:
com.example.orm.ArrayTuple object internals:
OFF  SZ                 TYPE DESCRIPTION               VALUE
  0   8                      (object header: mark)     0x0000000000000001 (non-biasable; age: 0)
  8   4                      (object header: class)    0x01119640
 12   4   java.lang.Object[] ArrayTuple.values         [180328214, 704599848, 822903257, 420244367, 415412882, 400965107, 17153961, 405828727, 697865814, 853543459, 21713888, 134861564, 147991908, 502768462, 485368663, 784596868, 103896983, 3445945, 314415156, 924871339, 210727179, 969831518, 362419484, 34913326, 119660363, 669420251, 343554372, 391710927, 681012125, 762872744, 285404198, 733600624, 18987777, 153656395, 70381881, 106293997, 59447443, 892137233, 929870545, 880882532, 311209903, 402288451, 818920130, 29333199, 218082331, 188259188, 194085621, 580314581, 288850906, 974420242, 610073023, 637091120, 238855868, 321520922, 573178718, 552975159, 653650666, 251196493, 457531752, 659291052, 466689708, 757382256, 557998550, 492045002, 456933701, 717934362, 128148146, 835209901, 805933458, 28253011, 265636151, 28153031, 903996340, 870817399, 736585262, 359062282, 300110954, 771651564, 566075654, 968535715, 847454780, 175019190, 829035241, 240817022, 878739061, 288995125, 377741673, 292590654, 359899850, 176709399, 428868219, 496427131, 338641872, 36542392, 442740607, 117050435, 883569297, 48018557, 902506422, 470335121]
Instance size: 16 bytes
Space losses: 0 bytes internal + 0 bytes external = 0 bytes total

ArrayTuple graph layout (including referenced objects):
com.example.orm.ArrayTuple@3e11f9e9d footprint:
     COUNT       AVG       SUM   DESCRIPTION
         1       416       416   [Ljava.lang.Object;
         1        16        16   com.example.orm.ArrayTuple
        30        16       480   java.lang.Integer
        70        24      1680   java.lang.Long
       102                2592   (total)

         */
    }
}

class TupleDemo :
    AbstractTuple() {
    var item0: Int = 0
    var item1: Int = 0
    var item2: Int = 0
    var item3: Int = 0
    var item4: Int = 0
    var item5: Int = 0
    var item6: Int = 0
    var item7: Int = 0
    var item8: Int = 0
    var item9: Int = 0
    var item10: Int = 0
    var item11: Int = 0
    var item12: Int = 0
    var item13: Int = 0
    var item14: Int = 0
    var item15: Int = 0
    var item16: Int = 0
    var item17: Int = 0
    var item18: Int = 0
    var item19: Int = 0
    var item20: Int = 0
    var item21: Int = 0
    var item22: Int = 0
    var item23: Int = 0
    var item24: Int = 0
    var item25: Int = 0
    var item26: Int = 0
    var item27: Int = 0
    var item28: Int = 0
    var item29: Int = 0
    var item30: Long = 0
    var item31: Long = 0
    var item32: Long = 0
    var item33: Long = 0
    var item34: Long = 0
    var item35: Long = 0
    var item36: Long = 0
    var item37: Long = 0
    var item38: Long = 0
    var item39: Long = 0
    var item40: Long = 0
    var item41: Long = 0
    var item42: Long = 0
    var item43: Long = 0
    var item44: Long = 0
    var item45: Long = 0
    var item46: Long = 0
    var item47: Long = 0
    var item48: Long = 0
    var item49: Long = 0
    var item50: Long = 0
    var item51: Long = 0
    var item52: Long = 0
    var item53: Long = 0
    var item54: Long = 0
    var item55: Long = 0
    var item56: Long = 0
    var item57: Long = 0
    var item58: Long = 0
    var item59: Long = 0
    var item60: Long = 0
    var item61: Long = 0
    var item62: Long = 0
    var item63: Long = 0
    var item64: Long = 0
    var item65: Long = 0
    var item66: Long = 0
    var item67: Long = 0
    var item68: Long = 0
    var item69: Long = 0
    var item70: Long = 0
    var item71: Long = 0
    var item72: Long = 0
    var item73: Long = 0
    var item74: Long = 0
    var item75: Long = 0
    var item76: Long = 0
    var item77: Long = 0
    var item78: Long = 0
    var item79: Long = 0
    var item80: Long = 0
    var item81: Long = 0
    var item82: Long = 0
    var item83: Long = 0
    var item84: Long = 0
    var item85: Long = 0
    var item86: Long = 0
    var item87: Long = 0
    var item88: Long = 0
    var item89: Long = 0
    var item90: Long = 0
    var item91: Long = 0
    var item92: Long = 0
    var item93: Long = 0
    var item94: Long = 0
    var item95: Long = 0
    var item96: Long = 0
    var item97: Long = 0
    var item98: Long = 0
    var item99: Long = 0

    override val directSize: Int
        get() = 100

    override fun getFieldType(index: Int): Class<*> {
        if (index < 0) {
            return throwIndexOutOfBounds<Class<*>?>(index)!!
        }
        when (index) {
            0 -> return Int::class.javaPrimitiveType!!
            1 -> return Int::class.javaPrimitiveType!!
            2 -> return Int::class.javaPrimitiveType!!
            3 -> return Int::class.javaPrimitiveType!!
            4 -> return Int::class.javaPrimitiveType!!
            5 -> return Int::class.javaPrimitiveType!!
            6 -> return Int::class.javaPrimitiveType!!
            7 -> return Int::class.javaPrimitiveType!!
            8 -> return Int::class.javaPrimitiveType!!
            9 -> return Int::class.javaPrimitiveType!!
            10 -> return Int::class.javaPrimitiveType!!
            11 -> return Int::class.javaPrimitiveType!!
            12 -> return Int::class.javaPrimitiveType!!
            13 -> return Int::class.javaPrimitiveType!!
            14 -> return Int::class.javaPrimitiveType!!
            15 -> return Int::class.javaPrimitiveType!!
            16 -> return Int::class.javaPrimitiveType!!
            17 -> return Int::class.javaPrimitiveType!!
            18 -> return Int::class.javaPrimitiveType!!
            19 -> return Int::class.javaPrimitiveType!!
            20 -> return Int::class.javaPrimitiveType!!
            21 -> return Int::class.javaPrimitiveType!!
            22 -> return Int::class.javaPrimitiveType!!
            23 -> return Int::class.javaPrimitiveType!!
            24 -> return Int::class.javaPrimitiveType!!
            25 -> return Int::class.javaPrimitiveType!!
            26 -> return Int::class.javaPrimitiveType!!
            27 -> return Int::class.javaPrimitiveType!!
            28 -> return Int::class.javaPrimitiveType!!
            29 -> return Int::class.javaPrimitiveType!!
            30 -> return Long::class.javaPrimitiveType!!
            31 -> return Long::class.javaPrimitiveType!!
            32 -> return Long::class.javaPrimitiveType!!
            33 -> return Long::class.javaPrimitiveType!!
            34 -> return Long::class.javaPrimitiveType!!
            35 -> return Long::class.javaPrimitiveType!!
            36 -> return Long::class.javaPrimitiveType!!
            37 -> return Long::class.javaPrimitiveType!!
            38 -> return Long::class.javaPrimitiveType!!
            39 -> return Long::class.javaPrimitiveType!!
            40 -> return Long::class.javaPrimitiveType!!
            41 -> return Long::class.javaPrimitiveType!!
            42 -> return Long::class.javaPrimitiveType!!
            43 -> return Long::class.javaPrimitiveType!!
            44 -> return Long::class.javaPrimitiveType!!
            45 -> return Long::class.javaPrimitiveType!!
            46 -> return Long::class.javaPrimitiveType!!
            47 -> return Long::class.javaPrimitiveType!!
            48 -> return Long::class.javaPrimitiveType!!
            49 -> return Long::class.javaPrimitiveType!!
            50 -> return Long::class.javaPrimitiveType!!
            51 -> return Long::class.javaPrimitiveType!!
            52 -> return Long::class.javaPrimitiveType!!
            53 -> return Long::class.javaPrimitiveType!!
            54 -> return Long::class.javaPrimitiveType!!
            55 -> return Long::class.javaPrimitiveType!!
            56 -> return Long::class.javaPrimitiveType!!
            57 -> return Long::class.javaPrimitiveType!!
            58 -> return Long::class.javaPrimitiveType!!
            59 -> return Long::class.javaPrimitiveType!!
            60 -> return Long::class.javaPrimitiveType!!
            61 -> return Long::class.javaPrimitiveType!!
            62 -> return Long::class.javaPrimitiveType!!
            63 -> return Long::class.javaPrimitiveType!!
            64 -> return Long::class.javaPrimitiveType!!
            65 -> return Long::class.javaPrimitiveType!!
            66 -> return Long::class.javaPrimitiveType!!
            67 -> return Long::class.javaPrimitiveType!!
            68 -> return Long::class.javaPrimitiveType!!
            69 -> return Long::class.javaPrimitiveType!!
            70 -> return Long::class.javaPrimitiveType!!
            71 -> return Long::class.javaPrimitiveType!!
            72 -> return Long::class.javaPrimitiveType!!
            73 -> return Long::class.javaPrimitiveType!!
            74 -> return Long::class.javaPrimitiveType!!
            75 -> return Long::class.javaPrimitiveType!!
            76 -> return Long::class.javaPrimitiveType!!
            77 -> return Long::class.javaPrimitiveType!!
            78 -> return Long::class.javaPrimitiveType!!
            79 -> return Long::class.javaPrimitiveType!!
            80 -> return Long::class.javaPrimitiveType!!
            81 -> return Long::class.javaPrimitiveType!!
            82 -> return Long::class.javaPrimitiveType!!
            83 -> return Long::class.javaPrimitiveType!!
            84 -> return Long::class.javaPrimitiveType!!
            85 -> return Long::class.javaPrimitiveType!!
            86 -> return Long::class.javaPrimitiveType!!
            87 -> return Long::class.javaPrimitiveType!!
            88 -> return Long::class.javaPrimitiveType!!
            89 -> return Long::class.javaPrimitiveType!!
            90 -> return Long::class.javaPrimitiveType!!
            91 -> return Long::class.javaPrimitiveType!!
            92 -> return Long::class.javaPrimitiveType!!
            93 -> return Long::class.javaPrimitiveType!!
            94 -> return Long::class.javaPrimitiveType!!
            95 -> return Long::class.javaPrimitiveType!!
            96 -> return Long::class.javaPrimitiveType!!
            97 -> return Long::class.javaPrimitiveType!!
            98 -> return Long::class.javaPrimitiveType!!
            99 -> return Long::class.javaPrimitiveType!!
            else -> return throwIndexOutOfBounds<Class<*>?>(index)!!
        }
    }

    override fun getItem(index: Int): Any? {
        if (index < 0) {
            return throwIndexOutOfBounds<Any?>(index)
        }
        when (index) {
            0 -> return this.item0
            1 -> return this.item1
            2 -> return this.item2
            3 -> return this.item3
            4 -> return this.item4
            5 -> return this.item5
            6 -> return this.item6
            7 -> return this.item7
            8 -> return this.item8
            9 -> return this.item9
            10 -> return this.item10
            11 -> return this.item11
            12 -> return this.item12
            13 -> return this.item13
            14 -> return this.item14
            15 -> return this.item15
            16 -> return this.item16
            17 -> return this.item17
            18 -> return this.item18
            19 -> return this.item19
            20 -> return this.item20
            21 -> return this.item21
            22 -> return this.item22
            23 -> return this.item23
            24 -> return this.item24
            25 -> return this.item25
            26 -> return this.item26
            27 -> return this.item27
            28 -> return this.item28
            29 -> return this.item29
            30 -> return this.item30
            31 -> return this.item31
            32 -> return this.item32
            33 -> return this.item33
            34 -> return this.item34
            35 -> return this.item35
            36 -> return this.item36
            37 -> return this.item37
            38 -> return this.item38
            39 -> return this.item39
            40 -> return this.item40
            41 -> return this.item41
            42 -> return this.item42
            43 -> return this.item43
            44 -> return this.item44
            45 -> return this.item45
            46 -> return this.item46
            47 -> return this.item47
            48 -> return this.item48
            49 -> return this.item49
            50 -> return this.item50
            51 -> return this.item51
            52 -> return this.item52
            53 -> return this.item53
            54 -> return this.item54
            55 -> return this.item55
            56 -> return this.item56
            57 -> return this.item57
            58 -> return this.item58
            59 -> return this.item59
            60 -> return this.item60
            61 -> return this.item61
            62 -> return this.item62
            63 -> return this.item63
            64 -> return this.item64
            65 -> return this.item65
            66 -> return this.item66
            67 -> return this.item67
            68 -> return this.item68
            69 -> return this.item69
            70 -> return this.item70
            71 -> return this.item71
            72 -> return this.item72
            73 -> return this.item73
            74 -> return this.item74
            75 -> return this.item75
            76 -> return this.item76
            77 -> return this.item77
            78 -> return this.item78
            79 -> return this.item79
            80 -> return this.item80
            81 -> return this.item81
            82 -> return this.item82
            83 -> return this.item83
            84 -> return this.item84
            85 -> return this.item85
            86 -> return this.item86
            87 -> return this.item87
            88 -> return this.item88
            89 -> return this.item89
            90 -> return this.item90
            91 -> return this.item91
            92 -> return this.item92
            93 -> return this.item93
            94 -> return this.item94
            95 -> return this.item95
            96 -> return this.item96
            97 -> return this.item97
            98 -> return this.item98
            99 -> return this.item99
            else -> return throwIndexOutOfBounds<Any?>(index)
        }
    }

    override fun setItem(index: Int, value: Any?) {
        if (index < 0) {
            throwIndexOutOfBounds<Any?>(index)
            return
        }
        when (index) {
            0 -> {
                this.item0 = (value as Int)
                return
            }

            1 -> {
                this.item1 = (value as Int)
                return
            }

            2 -> {
                this.item2 = (value as Int)
                return
            }

            3 -> {
                this.item3 = (value as Int)
                return
            }

            4 -> {
                this.item4 = (value as Int)
                return
            }

            5 -> {
                this.item5 = (value as Int)
                return
            }

            6 -> {
                this.item6 = (value as Int)
                return
            }

            7 -> {
                this.item7 = (value as Int)
                return
            }

            8 -> {
                this.item8 = (value as Int)
                return
            }

            9 -> {
                this.item9 = (value as Int)
                return
            }

            10 -> {
                this.item10 = (value as Int)
                return
            }

            11 -> {
                this.item11 = (value as Int)
                return
            }

            12 -> {
                this.item12 = (value as Int)
                return
            }

            13 -> {
                this.item13 = (value as Int)
                return
            }

            14 -> {
                this.item14 = (value as Int)
                return
            }

            15 -> {
                this.item15 = (value as Int)
                return
            }

            16 -> {
                this.item16 = (value as Int)
                return
            }

            17 -> {
                this.item17 = (value as Int)
                return
            }

            18 -> {
                this.item18 = (value as Int)
                return
            }

            19 -> {
                this.item19 = (value as Int)
                return
            }

            20 -> {
                this.item20 = (value as Int)
                return
            }

            21 -> {
                this.item21 = (value as Int)
                return
            }

            22 -> {
                this.item22 = (value as Int)
                return
            }

            23 -> {
                this.item23 = (value as Int)
                return
            }

            24 -> {
                this.item24 = (value as Int)
                return
            }

            25 -> {
                this.item25 = (value as Int)
                return
            }

            26 -> {
                this.item26 = (value as Int)
                return
            }

            27 -> {
                this.item27 = (value as Int)
                return
            }

            28 -> {
                this.item28 = (value as Int)
                return
            }

            29 -> {
                this.item29 = (value as Int)
                return
            }

            30 -> {
                this.item30 = (value as Long)
                return
            }

            31 -> {
                this.item31 = (value as Long)
                return
            }

            32 -> {
                this.item32 = (value as Long)
                return
            }

            33 -> {
                this.item33 = (value as Long)
                return
            }

            34 -> {
                this.item34 = (value as Long)
                return
            }

            35 -> {
                this.item35 = (value as Long)
                return
            }

            36 -> {
                this.item36 = (value as Long)
                return
            }

            37 -> {
                this.item37 = (value as Long)
                return
            }

            38 -> {
                this.item38 = (value as Long)
                return
            }

            39 -> {
                this.item39 = (value as Long)
                return
            }

            40 -> {
                this.item40 = (value as Long)
                return
            }

            41 -> {
                this.item41 = (value as Long)
                return
            }

            42 -> {
                this.item42 = (value as Long)
                return
            }

            43 -> {
                this.item43 = (value as Long)
                return
            }

            44 -> {
                this.item44 = (value as Long)
                return
            }

            45 -> {
                this.item45 = (value as Long)
                return
            }

            46 -> {
                this.item46 = (value as Long)
                return
            }

            47 -> {
                this.item47 = (value as Long)
                return
            }

            48 -> {
                this.item48 = (value as Long)
                return
            }

            49 -> {
                this.item49 = (value as Long)
                return
            }

            50 -> {
                this.item50 = (value as Long)
                return
            }

            51 -> {
                this.item51 = (value as Long)
                return
            }

            52 -> {
                this.item52 = (value as Long)
                return
            }

            53 -> {
                this.item53 = (value as Long)
                return
            }

            54 -> {
                this.item54 = (value as Long)
                return
            }

            55 -> {
                this.item55 = (value as Long)
                return
            }

            56 -> {
                this.item56 = (value as Long)
                return
            }

            57 -> {
                this.item57 = (value as Long)
                return
            }

            58 -> {
                this.item58 = (value as Long)
                return
            }

            59 -> {
                this.item59 = (value as Long)
                return
            }

            60 -> {
                this.item60 = (value as Long)
                return
            }

            61 -> {
                this.item61 = (value as Long)
                return
            }

            62 -> {
                this.item62 = (value as Long)
                return
            }

            63 -> {
                this.item63 = (value as Long)
                return
            }

            64 -> {
                this.item64 = (value as Long)
                return
            }

            65 -> {
                this.item65 = (value as Long)
                return
            }

            66 -> {
                this.item66 = (value as Long)
                return
            }

            67 -> {
                this.item67 = (value as Long)
                return
            }

            68 -> {
                this.item68 = (value as Long)
                return
            }

            69 -> {
                this.item69 = (value as Long)
                return
            }

            70 -> {
                this.item70 = (value as Long)
                return
            }

            71 -> {
                this.item71 = (value as Long)
                return
            }

            72 -> {
                this.item72 = (value as Long)
                return
            }

            73 -> {
                this.item73 = (value as Long)
                return
            }

            74 -> {
                this.item74 = (value as Long)
                return
            }

            75 -> {
                this.item75 = (value as Long)
                return
            }

            76 -> {
                this.item76 = (value as Long)
                return
            }

            77 -> {
                this.item77 = (value as Long)
                return
            }

            78 -> {
                this.item78 = (value as Long)
                return
            }

            79 -> {
                this.item79 = (value as Long)
                return
            }

            80 -> {
                this.item80 = (value as Long)
                return
            }

            81 -> {
                this.item81 = (value as Long)
                return
            }

            82 -> {
                this.item82 = (value as Long)
                return
            }

            83 -> {
                this.item83 = (value as Long)
                return
            }

            84 -> {
                this.item84 = (value as Long)
                return
            }

            85 -> {
                this.item85 = (value as Long)
                return
            }

            86 -> {
                this.item86 = (value as Long)
                return
            }

            87 -> {
                this.item87 = (value as Long)
                return
            }

            88 -> {
                this.item88 = (value as Long)
                return
            }

            89 -> {
                this.item89 = (value as Long)
                return
            }

            90 -> {
                this.item90 = (value as Long)
                return
            }

            91 -> {
                this.item91 = (value as Long)
                return
            }

            92 -> {
                this.item92 = (value as Long)
                return
            }

            93 -> {
                this.item93 = (value as Long)
                return
            }

            94 -> {
                this.item94 = (value as Long)
                return
            }

            95 -> {
                this.item95 = (value as Long)
                return
            }

            96 -> {
                this.item96 = (value as Long)
                return
            }

            97 -> {
                this.item97 = (value as Long)
                return
            }

            98 -> {
                this.item98 = (value as Long)
                return
            }

            99 -> {
                this.item99 = (value as Long)
                return
            }

            else -> throwIndexOutOfBounds<Any?>(index)
        }
    }

    override fun getInt(index: Int): Int {
        when (index) {
            0 -> return this.item0
            1 -> return this.item1
            2 -> return this.item2
            3 -> return this.item3
            4 -> return this.item4
            5 -> return this.item5
            6 -> return this.item6
            7 -> return this.item7
            8 -> return this.item8
            9 -> return this.item9
            10 -> return this.item10
            11 -> return this.item11
            12 -> return this.item12
            13 -> return this.item13
            14 -> return this.item14
            15 -> return this.item15
            16 -> return this.item16
            17 -> return this.item17
            18 -> return this.item18
            19 -> return this.item19
            20 -> return this.item20
            21 -> return this.item21
            22 -> return this.item22
            23 -> return this.item23
            24 -> return this.item24
            25 -> return this.item25
            26 -> return this.item26
            27 -> return this.item27
            28 -> return this.item28
            29 -> return this.item29
            else -> return super.getInt(index)
        }
    }

    public override fun getLong(index: Int): Long {
        when (index) {
            30 -> return this.item30
            31 -> return this.item31
            32 -> return this.item32
            33 -> return this.item33
            34 -> return this.item34
            35 -> return this.item35
            36 -> return this.item36
            37 -> return this.item37
            38 -> return this.item38
            39 -> return this.item39
            40 -> return this.item40
            41 -> return this.item41
            42 -> return this.item42
            43 -> return this.item43
            44 -> return this.item44
            45 -> return this.item45
            46 -> return this.item46
            47 -> return this.item47
            48 -> return this.item48
            49 -> return this.item49
            50 -> return this.item50
            51 -> return this.item51
            52 -> return this.item52
            53 -> return this.item53
            54 -> return this.item54
            55 -> return this.item55
            56 -> return this.item56
            57 -> return this.item57
            58 -> return this.item58
            59 -> return this.item59
            60 -> return this.item60
            61 -> return this.item61
            62 -> return this.item62
            63 -> return this.item63
            64 -> return this.item64
            65 -> return this.item65
            66 -> return this.item66
            67 -> return this.item67
            68 -> return this.item68
            69 -> return this.item69
            70 -> return this.item70
            71 -> return this.item71
            72 -> return this.item72
            73 -> return this.item73
            74 -> return this.item74
            75 -> return this.item75
            76 -> return this.item76
            77 -> return this.item77
            78 -> return this.item78
            79 -> return this.item79
            80 -> return this.item80
            81 -> return this.item81
            82 -> return this.item82
            83 -> return this.item83
            84 -> return this.item84
            85 -> return this.item85
            86 -> return this.item86
            87 -> return this.item87
            88 -> return this.item88
            89 -> return this.item89
            90 -> return this.item90
            91 -> return this.item91
            92 -> return this.item92
            93 -> return this.item93
            94 -> return this.item94
            95 -> return this.item95
            96 -> return this.item96
            97 -> return this.item97
            98 -> return this.item98
            99 -> return this.item99
            else -> return super.getLong(index)
        }
    }

    override fun setInt(index: Int, value: Int) {
        when (index) {
            0 -> {
                this.item0 = value
                return
            }

            1 -> {
                this.item1 = value
                return
            }

            2 -> {
                this.item2 = value
                return
            }

            3 -> {
                this.item3 = value
                return
            }

            4 -> {
                this.item4 = value
                return
            }

            5 -> {
                this.item5 = value
                return
            }

            6 -> {
                this.item6 = value
                return
            }

            7 -> {
                this.item7 = value
                return
            }

            8 -> {
                this.item8 = value
                return
            }

            9 -> {
                this.item9 = value
                return
            }

            10 -> {
                this.item10 = value
                return
            }

            11 -> {
                this.item11 = value
                return
            }

            12 -> {
                this.item12 = value
                return
            }

            13 -> {
                this.item13 = value
                return
            }

            14 -> {
                this.item14 = value
                return
            }

            15 -> {
                this.item15 = value
                return
            }

            16 -> {
                this.item16 = value
                return
            }

            17 -> {
                this.item17 = value
                return
            }

            18 -> {
                this.item18 = value
                return
            }

            19 -> {
                this.item19 = value
                return
            }

            20 -> {
                this.item20 = value
                return
            }

            21 -> {
                this.item21 = value
                return
            }

            22 -> {
                this.item22 = value
                return
            }

            23 -> {
                this.item23 = value
                return
            }

            24 -> {
                this.item24 = value
                return
            }

            25 -> {
                this.item25 = value
                return
            }

            26 -> {
                this.item26 = value
                return
            }

            27 -> {
                this.item27 = value
                return
            }

            28 -> {
                this.item28 = value
                return
            }

            29 -> {
                this.item29 = value
                return
            }

            else -> super.setInt(index, value)
        }
    }

    public override fun setLong(index: Int, value: Long) {
        when (index) {
            30 -> {
                this.item30 = value
                return
            }

            31 -> {
                this.item31 = value
                return
            }

            32 -> {
                this.item32 = value
                return
            }

            33 -> {
                this.item33 = value
                return
            }

            34 -> {
                this.item34 = value
                return
            }

            35 -> {
                this.item35 = value
                return
            }

            36 -> {
                this.item36 = value
                return
            }

            37 -> {
                this.item37 = value
                return
            }

            38 -> {
                this.item38 = value
                return
            }

            39 -> {
                this.item39 = value
                return
            }

            40 -> {
                this.item40 = value
                return
            }

            41 -> {
                this.item41 = value
                return
            }

            42 -> {
                this.item42 = value
                return
            }

            43 -> {
                this.item43 = value
                return
            }

            44 -> {
                this.item44 = value
                return
            }

            45 -> {
                this.item45 = value
                return
            }

            46 -> {
                this.item46 = value
                return
            }

            47 -> {
                this.item47 = value
                return
            }

            48 -> {
                this.item48 = value
                return
            }

            49 -> {
                this.item49 = value
                return
            }

            50 -> {
                this.item50 = value
                return
            }

            51 -> {
                this.item51 = value
                return
            }

            52 -> {
                this.item52 = value
                return
            }

            53 -> {
                this.item53 = value
                return
            }

            54 -> {
                this.item54 = value
                return
            }

            55 -> {
                this.item55 = value
                return
            }

            56 -> {
                this.item56 = value
                return
            }

            57 -> {
                this.item57 = value
                return
            }

            58 -> {
                this.item58 = value
                return
            }

            59 -> {
                this.item59 = value
                return
            }

            60 -> {
                this.item60 = value
                return
            }

            61 -> {
                this.item61 = value
                return
            }

            62 -> {
                this.item62 = value
                return
            }

            63 -> {
                this.item63 = value
                return
            }

            64 -> {
                this.item64 = value
                return
            }

            65 -> {
                this.item65 = value
                return
            }

            66 -> {
                this.item66 = value
                return
            }

            67 -> {
                this.item67 = value
                return
            }

            68 -> {
                this.item68 = value
                return
            }

            69 -> {
                this.item69 = value
                return
            }

            70 -> {
                this.item70 = value
                return
            }

            71 -> {
                this.item71 = value
                return
            }

            72 -> {
                this.item72 = value
                return
            }

            73 -> {
                this.item73 = value
                return
            }

            74 -> {
                this.item74 = value
                return
            }

            75 -> {
                this.item75 = value
                return
            }

            76 -> {
                this.item76 = value
                return
            }

            77 -> {
                this.item77 = value
                return
            }

            78 -> {
                this.item78 = value
                return
            }

            79 -> {
                this.item79 = value
                return
            }

            80 -> {
                this.item80 = value
                return
            }

            81 -> {
                this.item81 = value
                return
            }

            82 -> {
                this.item82 = value
                return
            }

            83 -> {
                this.item83 = value
                return
            }

            84 -> {
                this.item84 = value
                return
            }

            85 -> {
                this.item85 = value
                return
            }

            86 -> {
                this.item86 = value
                return
            }

            87 -> {
                this.item87 = value
                return
            }

            88 -> {
                this.item88 = value
                return
            }

            89 -> {
                this.item89 = value
                return
            }

            90 -> {
                this.item90 = value
                return
            }

            91 -> {
                this.item91 = value
                return
            }

            92 -> {
                this.item92 = value
                return
            }

            93 -> {
                this.item93 = value
                return
            }

            94 -> {
                this.item94 = value
                return
            }

            95 -> {
                this.item95 = value
                return
            }

            96 -> {
                this.item96 = value
                return
            }

            97 -> {
                this.item97 = value
                return
            }

            98 -> {
                this.item98 = value
                return
            }

            99 -> {
                this.item99 = value
                return
            }

            else -> super.setLong(index, value)
        }
    }
}