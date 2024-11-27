using System;
using System.Diagnostics;

namespace ConsoleApp1 {
    LongConverterTest.PerformanceTest();

    public class LongConverterTest {
        private static uint[][] GetDimensionInfo(int size) {
            return size switch {
                1 => [
                [900],
                [0, 1, 9, 5],    //min = 0, max: 9
                [900],[900],[900],

                [900],[900],[900],[900],[900],

                [900],
                    ],

                2 => [
                [900],
                [0, 1, 9, 5],    //min = 0, max: 9
                [900],
                [900, 832],    //min = 832,max = 900
                [900],

                [900],[900],[900],[900],[900],

                [900],
                    ],

                3 => [
                [900],
                [0, 1, 9, 5],    //min = 0, max: 9
                [900],
                [900, 832],    //min = 832,max = 900
                [3, 6, 8, 4, 2, 2, 44, 56, 67, 32, 123], //min=2, max=123

                [900],[900],[900],[900],[900],

                [900],
                ],

                4 => [
                [900],
                [0, 1, 9, 5],    //min = 0, max: 9
                [900],
                [900, 832],    //min = 832,max = 900
                [3, 6, 8, 4, 2, 2, 44, 56, 67, 32, 123], //min=2, max=123

                [900],
                [900],
                [10000000, 10000002, 10000001], //min=10000000, max=10000002
                [900],
                [900],

                [900],
                ],

                5 => [
                [900],
                [0, 1, 9, 5],    //min = 0, max: 9
                [900],
                [900, 832],    //min = 832,max = 900
                [3, 6, 8, 4, 2, 2, 44, 56, 67, 32, 123], //min=2, max=123

                [900],
                [900],
                [10000000, 10000002, 10000001], //min=10000000, max=10000002
                [900],
                [4, 9, 10],     //min=4, max =10

                [900],
                ],

                6 => [
                [900],
                [0, 1, 9, 5],    //min = 0, max: 9
                [900],
                [900, 832],    //min = 832,max = 900
                [3, 6, 8, 4, 2, 2, 44, 56, 67, 32, 123], //min=2, max=123

                [900],
                [900],
                [10000000, 10000002, 10000001], //min=10000000, max=10000002
                [900],
                [4, 9, 10],     //min=4, max =10

                [9, 4],        //min=4, max = 9
                ],
                7 => [
                [8,7],
                [0, 1, 9, 5],    //min = 0, max: 9
                [900],
                [900, 832],    //min = 832,max = 900
                [3, 6, 8, 4, 2, 2, 44, 56, 67, 32, 123], //min=2, max=123

                [900],
                [900],
                [1000_0000, 1000_0002, 1000_0001], //min=10000000, max=10000002
                [900],
                [4, 9, 10],     //min=4, max =10

                [9, 4],        //min=4, max = 9
                ],
                _ => [],
            };
        }

        private static void PerformanceTest(string name, LongConverter target) {
            uint[] key = [9, 5, 900, 832, 67, 800, 100_0000, 1000_0001, 3, 4, 9];
            ulong x = 0;
            Stopwatch stopwatch = Stopwatch.StartNew();

            for (uint i = 0; i < int.MaxValue; i++) {
                key[0] = i;
                x += target.ToX(key);
            }

            stopwatch.Stop();
            Console.WriteLine($"{name} : {stopwatch.ElapsedMilliseconds} ms.  result = {x}");
        }

        public static void PerformanceTest() {
            for (int i = 1; i < 8; i++) {
                PerformanceTest($"basic{i}", new LongConverter(GetDimensionInfo(i)));
            }
        }

        public static void PerformanceTestSimd() {
            for (int i = 1; i < 8; i++) {
                // PerformanceTest($"basic{i}", new LongConverter(GetDimensionInfo(i)).CreateSimdVersion());
            }
        }
    }

    /**
     * IConverter 对于 long 类型的特定接口，避免java的装箱行为。
     */
    public interface ILongConverter<Y> {
        long ToX(Y y);

        /**
         * 最大的可能值，不包括此值。
         */
        long Max { get; }
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
    public class LongConverter {
        //如果某个维度的成员只有一种可能，此变量的此维度位置将存放这个值。
        private readonly uint[] _fixedMembers;
        //如果维度的成员存在多种可能，那么存储在这个映射表。
        private readonly MemberMap[] _mappings;

        public LongConverter(uint[][] dimensionInfo) {
            _fixedMembers = new uint[dimensionInfo.Length];
            var list = new List<MemberMap>();
            ulong carry = 1; //乘法的因子

            for (int i = 0; i < dimensionInfo.Length; i++) {
                var dim = dimensionInfo[i];
                switch (dim.Length) {
                    case 0:
                        throw new ApplicationException("0");
                    //如果此维度仅仅一个可用成员，将跳过映射，例如在条件中，很多的维度被限制在一个成员中。
                    case 1:
                        _fixedMembers[i] = dim[0];
                        break;
                    default:
                        var map = new MemberMap(i, dim, carry);
                        if (map.Step == 0) //虽然给了多个，但是都是一样的值。
                        {
                            _fixedMembers[i] = map.MinValue;
                        }
                        else {
                            carry *= map.Step;
                            list.Add(map);
                        }
                        break;
                }
            }
            _mappings = [.. list];
        }

        /**
         * 根据一个给定的维度组合找到所有可能的组合中按照某个确定顺序排列后的位置
         */
        public ulong ToX(uint[] y) {
            ulong result = 0;
            foreach (var mapping in _mappings) {
                //从有效维度中获取成员的值，并减去最小值，作为实际运算的值
                result += mapping.ToX(y);
            }

            return result;
        }

        public long Max => throw new NotImplementedException("Not yet implemented");

        internal class MemberMap {
            public int DimensionPosition { get; }
            public ulong Carry { get; }
            public uint MinValue { get; }
            private readonly uint MaxValue;

            public MemberMap(int dimensionPosition, uint[] members, ulong carry) {
                DimensionPosition = dimensionPosition;
                Carry = carry;
                MinValue = members.Min();
                MaxValue = members.Max();
            }

            public (ulong) Step => (ulong)MaxValue - (ulong)MinValue + 1;

            public ulong ToX(uint[] y) => Carry * (y[DimensionPosition] - MinValue);

            public override string ToString() {
                return $"{{pos:{DimensionPosition}, min:{MinValue}, max:{MaxValue}, step:{Step}, carry:{Carry}}}";
            }
        }

        /*public ILongConverter<int[]> CreateSimdVersion()
        {
            return LongConverterSimd.Create(_mappings, this);
        }*/
    }
}
