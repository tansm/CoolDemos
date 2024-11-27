using System;
using System.Collections.Generic;
using System.Linq;
using System.Numerics;
using System.Runtime.CompilerServices;
using System.Runtime.Intrinsics;
using System.Text;
using System.Threading.Tasks;

namespace VectorDemo;

internal static class VectorUtils {

    // No matter whether the hardware supports it or not,
    // use Vector512 directly because Vector512 has an automatic downgrade feature.

    // 无论硬件是否支持，都直接使用 Vector512， 因为 Vector512 拥有自动降级特性。
    public static void Vector512And(long[] a, long[] b) {
        uint count = (uint)a.Length;
        ref long x = ref a[0];
        ref long y = ref b[0];
        uint j = 0;
        if (count >= Vector512<long>.Count) {
            for (; j < count - (Vector512<long>.Count - 1u); j += (uint)Vector512<long>.Count) {
                Vector512<long> result = Vector512.LoadUnsafe(ref x, j) & Vector512.LoadUnsafe(ref y, j);
                result.StoreUnsafe(ref x, j);
            }
        }

        for (; j < count; j++) {
            a[j] &= b[j];
        }
    }

    // By judging whether the hardware supports a certain feature, write different code, which is simplified here.
    // The actual code needs to detect 512, 256, 128. You can refer to:
    // https://github.com/dotnet/runtime/blob/main/src/libraries/System.Collections/src/System/Collections/BitArray.cs

    // 通过判断硬件是否支持某个特性，编写不同的代码，这里做了简化，实际代码要检测 512，256，128，可以参考：
    // https://github.com/dotnet/runtime/blob/main/src/libraries/System.Collections/src/System/Collections/BitArray.cs
    public static void Vector256And(long[] a, long[] b) {
        uint count = (uint)a.Length;
        ref long x = ref a[0];
        ref long y = ref b[0];
        uint j = 0;
        if (Vector256.IsHardwareAccelerated && count >= Vector256<long>.Count) {
            for (; j < count - (Vector256<long>.Count - 1u); j += (uint)Vector256<long>.Count) {
                Vector256<long> result = Vector256.LoadUnsafe(ref x, j) & Vector256.LoadUnsafe(ref y, j);
                result.StoreUnsafe(ref x, j);
            }
        }

        for (; j < count; j++) {
            a[j] &= b[j];
        }
    }

    // Use the standard Vector<T> struct
    // 使用标准的 Vector<T> 结构
    public static void VectorAnd(long[] a, long[] b) {
        uint count = (uint)a.Length;
        ref long x = ref a[0];
        ref long y = ref b[0];
        uint j = 0;
        if (count >= Vector<long>.Count) {
            for (; j < count - (Vector<long>.Count - 1u); j += (uint)Vector<long>.Count) {
                Vector<long> result = Vector.LoadUnsafe(ref x, j) & Vector.LoadUnsafe(ref y, j);
                result.StoreUnsafe(ref x, j);
            }
        }

        for (; j < count; j++) {
            a[j] &= b[j];
        }
    }

    public static unsafe void AlignedVectorAnd(Span<long> a, Span<long> b) {
        nuint count = (uint)a.Length;
        fixed(long* ptrA = a, ptrB = b){
            // 检查是否已经对齐到 32 字节边界（AVX2 要求）
            if ((((nuint)ptrA % (nuint)sizeof(Vector<byte>)) == 0) && (((nuint)ptrB % (nuint)sizeof(Vector<byte>)) == 0)) {
                nuint j = 0;

                // 处理对齐部分
                for (; j <= count - (nuint)Vector<long>.Count; j += (nuint)Vector<long>.Count) {
                    var vecA = Vector.LoadAligned(ptrA + j);
                    var vecB = Vector.LoadAligned(ptrB + j);

                    var result = vecA & vecB;
                    result.StoreAligned(ptrA + j);
                }

                // 处理剩余非对齐元素
                for (; j < count; j++) {
                    a[(int)j] &= b[(int)j];
                }
            }
            else {
                throw new InvalidOperationException("Memory is not aligned to 32 bytes.");
            }
        }
    }

    // No vectorization operations are used.
    // 不使用向量化操作。
    public static void BasicAnd(long[] a, long[] b) {
        uint count = (uint)a.Length;
        for (uint j = 0; j < count; j++) {
            a[j] &= b[j];
        }
    }
}
