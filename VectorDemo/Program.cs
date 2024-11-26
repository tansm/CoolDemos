using BenchmarkDotNet.Attributes;
using BenchmarkDotNet.Running;
using System.Runtime.Intrinsics;

namespace VectorDemo;

public class Benchmark {
    private long[] a;
    private long[] b;

    [GlobalSetup]
    public void Setup() {
        uint count = 10002;
        a = new long[count];
        b = new long[count];
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

public class Program {
    public static void Main(string[] args) {
        Console.WriteLine($"Vector512.IsHardwareAccelerated = {Vector512.IsHardwareAccelerated}");
        Console.WriteLine($"Vector256.IsHardwareAccelerated = {Vector256.IsHardwareAccelerated}");

        BenchmarkRunner.Run<Benchmark>();
    }
}