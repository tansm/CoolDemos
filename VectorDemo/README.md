(English)
## Vector demo 
I tested three methods of using `Vector` and compared their performance:

- Automatic fallback from `Vector512` to `Vector256` computation.
- Direct computation using `Vector256`.
- Automatic vectorization with `Vector<long>`.

### Test Results

I ran the benchmark on two machines. The first machine does **not** support AVX-512.

```
BenchmarkDotNet v0.14.0, Windows 11 (10.0.26100.1742)
Intel Core i5-10500 CPU 3.10GHz, 1 CPU, 12 logical and 6 physical cores
.NET SDK 9.0.100
  [Host]     : .NET 9.0.0 (9.0.24.52809), X64 RyuJIT AVX2
  DefaultJob : .NET 9.0.0 (9.0.24.52809), X64 RyuJIT AVX2
```

| Method    | Mean     | Error     | StdDev    |
|---------- |---------:|----------:|----------:|
| Vector256 | 1.506 us | 0.0150 us | 0.0140 us |
| Vector512 | 1.471 us | 0.0111 us | 0.0099 us |
| Vector    | 2.009 us | 0.0377 us | 0.0403 us |
| Basic     | 4.820 us | 0.0254 us | 0.0212 us |

The initial conclusions seem to suggest:

- The automatic downgrade mechanism of the new `.NET` `Vector512` performs well.
- There's no need to write lengthy code to check hardware support for specific features.
- Using `Vector<T>` for simplicity isn't ideal due to its lower performance.

**However...**

I ran the benchmarks on a second machine with a newer CPU that **supports AVX-512**, allowing `Vector512` to execute actual AVX-512 instructions without downgrading.  

For the `Vector256` test, since no AVX-512 detection was involved, it ran using 256-bit instructions.  

`Vector` should automatically detect AVX-512 support and use those instructions.

```
BenchmarkDotNet v0.14.0, Windows 11 (10.0.22631.4391/23H2/2023Update/SunValley3)
AMD Ryzen 7 7840HS w/ Radeon 780M Graphics, 1 CPU, 16 logical and 8 physical cores
.NET SDK 9.0.100-rc.2.24474.11
  [Host]     : .NET 9.0.0 (9.0.24.47305), X64 RyuJIT AVX-512F+CD+BW+DQ+VL+VBMI
  DefaultJob : .NET 9.0.0 (9.0.24.47305), X64 RyuJIT AVX-512F+CD+BW+DQ+VL+VBMI
```

| Method    | Mean     | Error     | StdDev    |
|---------- |---------:|----------:|----------:|
| Vector256 | 1.411 us | 0.0029 us | 0.0028 us |
| Vector512 | 1.293 us | 0.0082 us | 0.0077 us |
| Vector    | 1.092 us | 0.0101 us | 0.0095 us |
| Basic     | 3.210 us | 0.0201 us | 0.0188 us |

These results suggest entirely different conclusions:

- Using `Vector<T>` yields the best performance optimizations.
- Don't expect 512-bit instructions to be **twice** as fast as 256-bit ones; the performance gains are marginal.
- AVX-512 bandwidth, even when processing `Int64`, does not achieve an 8x performance boost—**3x** is more realistic.

How should we summarize these contradictory results?

- Factors such as Windows version, CPU, and .NET runtime can all impact performance. Always benchmark your code thoroughly instead of relying solely on assumptions or prior experiences.
- Were there any lessons learned from this test? It seems that using `Vector<T>` is a way that doesn't easily lead to major mistakes.

### Supplement
I added tests to measure how much aligned memory access affects performance. On my i5 CPU (which does not support AVX-512), it improved performance by approximately 25%.

| Method        | Mean     | Error     | StdDev    |
|-------------- |---------:|----------:|----------:|
| Vector        | 2.009 us | 0.0377 us | 0.0403 us |
| AlignedVector | 1.609 us | 0.0308 us | 0.0390 us |


(中文)
## 向量演示

我测试了三种使用Vector的方法，测试其性能表现：
- 从Vector512自动回退到Vector256的计算；
- 使用Vector256的直接计算；
- 使用Vector\<long>的自动向量化。

### 测试结果

我在两台机器上运行了这个基准测试，第一台计算机不支持 AVX-512.
```
BenchmarkDotNet v0.14.0, Windows 11 (10.0.26100.1742)
Intel Core i5-10500 CPU 3.10GHz, 1 CPU, 12 logical and 6 physical cores
.NET SDK 9.0.100
  [Host]     : .NET 9.0.0 (9.0.24.52809), X64 RyuJIT AVX2
  DefaultJob : .NET 9.0.0 (9.0.24.52809), X64 RyuJIT AVX2
```

| Method    | Mean     | Error     | StdDev    |
|---------- |---------:|----------:|----------:|
| Vector256 | 1.506 us | 0.0150 us | 0.0140 us |
| Vector512 | 1.471 us | 0.0111 us | 0.0099 us |
| Vector    | 2.009 us | 0.0377 us | 0.0403 us |
| Basic     | 4.820 us | 0.0254 us | 0.0212 us |

`似乎` 能够得出以下结论：
- .net 新提供的 Vector512 自动降级机制，能够达到很好的性能；
- 不必像以前那样编写很长的代码,不断的判断硬件是否支持某个功能；
- 更没有必要为了简单性，使用 Vector\<T> 功能，其性能不佳； 

但是，但是...

我又找了另外一台计算机，新机器CPU比较新，支持 AVX-512，所以Vector512会按照实际的指令执行（不用降级），
Vector256测试中，由于没有AVX-512的检测，所以实际按照 256位 方式执行。

Vector 应该会自动检测CPU支持 AVX-512，所以使用 AVX-512 指令执行。

```
BenchmarkDotNet v0.14.0, Windows 11 (10.0.22631.4391/23H2/2023Update/SunValley3)
AMD Ryzen 7 7840HS w/ Radeon 780M Graphics, 1 CPU, 16 logical and 8 physical cores
.NET SDK 9.0.100-rc.2.24474.11
  [Host]     : .NET 9.0.0 (9.0.24.47305), X64 RyuJIT AVX-512F+CD+BW+DQ+VL+VBMI
  DefaultJob : .NET 9.0.0 (9.0.24.47305), X64 RyuJIT AVX-512F+CD+BW+DQ+VL+VBMI
```

| Method    | Mean     | Error     | StdDev    |
|---------- |---------:|----------:|----------:|
| Vector256 | 1.411 us | 0.0029 us | 0.0028 us |
| Vector512 | 1.293 us | 0.0082 us | 0.0077 us |
| Vector    | 1.092 us | 0.0101 us | 0.0095 us |
| Basic     | 3.210 us | 0.0201 us | 0.0188 us |

上面的测试结果，似乎得出完全不同的结论：
- 直接使用 Vector\<T>，其性能优化最好了；
- 不要期望 512 位就是 256 位性能的两倍，事实上，其性能提升微乎其微；
- 不要期望 AVX-512 处理带宽是 Int64 的 8倍，其性能就有 8 倍的提升，实际只有3倍；

那么，如此完全相反的结论，该如何总结呢？ 

- Windows、CPU、.net 的版本都不同，影响的因素很多，所以任何代码还是要多做基准测试，不要太相信经验；
- 这次测试有总结经验吗？ 似乎使用 Vector\<T> 是个不太犯大错的方式；

### 补充

我增加了测试，测量使用对齐方式对性能影响有多大。在我的 i5 CPU机器上（不支持AVX-512)，能提高25%左右。

| Method        | Mean     | Error     | StdDev    |
|-------------- |---------:|----------:|----------:|
| Vector        | 2.009 us | 0.0377 us | 0.0403 us |
| AlignedVector | 1.609 us | 0.0308 us | 0.0390 us |