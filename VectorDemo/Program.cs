using BenchmarkDotNet.Attributes;
using BenchmarkDotNet.Running;
using System.Runtime.InteropServices;
using System.Runtime.Intrinsics;

namespace VectorDemo;

public class Benchmark {
    private long[] a;
    private long[] b;

    [GlobalSetup]
    public void Setup() {
        //uint count = 10002;
        a = GC.AllocateArray<long>(length: 10000, pinned: true);
        b = GC.AllocateArray<long>(length: 10000, pinned: true);
        var r = new Random();
        for (uint i = 0; i < a.Length; i++) {
            a[i] = r.NextInt64();
            b[i] = r.NextInt64();
        }
    }

    [Benchmark]
    public void Vector256() {
        VectorUtils.Vector512And(a, b);
    }

    [Benchmark]
    public void Vector512() {
        VectorUtils.Vector512And(a, b);
    }

    [Benchmark]
    public void Vector() {
        VectorUtils.VectorAnd(a, b);
    }

    [Benchmark]
    public void Basic() {
        VectorUtils.BasicAnd(a, b);
    }
}


public class Benchmark2 {
    private IntPtr ptrA;
    private IntPtr ptrB;
    private int length = 10000;

    [GlobalSetup]
    public void Setup() {
        //uint count = 10002;
        var r = new Random();
        unsafe {
            
            long alignment = 64;
            ptrA = (IntPtr)NativeMemory.AlignedAlloc((nuint)(length * sizeof(long)), (nuint)alignment);
            ptrB = (IntPtr)NativeMemory.AlignedAlloc((nuint)(length * sizeof(long)), (nuint)alignment);
            
            Span<long> a = new Span<long>((void*)ptrA, length);
            Span<long> b = new Span<long>((void*)ptrB, length);

            for (int i = 0; i < a.Length; i++) {
                a[i] = r.NextInt64();
                b[i] = r.NextInt64();
            }
        }
    }

    [GlobalCleanup]
    public void Cleanup() {
        unsafe {
            NativeMemory.AlignedFree((void*)ptrA);
            NativeMemory.AlignedFree((void*)ptrB);
        }
    }

    [Benchmark]
    public void AlignedVector() {
        unsafe {
            Span<long> a = new Span<long>((void*)ptrA, length);
            Span<long> b = new Span<long>((void*)ptrB, length);
            VectorUtils.AlignedVectorAnd(a, b);
        }
    }
}

public class Program {
    public static void Main(string[] args) {
        Console.WriteLine($"Vector512.IsHardwareAccelerated = {Vector512.IsHardwareAccelerated}");
        Console.WriteLine($"Vector256.IsHardwareAccelerated = {Vector256.IsHardwareAccelerated}");

        BenchmarkRunner.Run<Benchmark>();
        BenchmarkRunner.Run<Benchmark2>();
    }
}