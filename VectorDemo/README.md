(English)
## Vector demo 

We tested three methods of using Vector to test its performance:
- Automatically fallback from Vector512 to Vector256 calculations.
- Direct calculations using Vector256.
- Automatic vectorization using Vector \<long>.

- ### test result
BenchmarkDotNet v0.14.0, Windows 11 (10.0.22631.4391/23H2/2023Update/SunValley3)

AMD Ryzen 7 7840HS w/ Radeon 780M Graphics, 1 CPU, 16 logical and 8 physical cores

.NET SDK 9.0.100-rc.2.24474.11

  [Host]     : .NET 9.0.0 (9.0.24.47305), X64 RyuJIT AVX-512F+CD+BW+DQ+VL+VBMI

  DefaultJob : .NET 9.0.0 (9.0.24.47305), X64 RyuJIT AVX-512F+CD+BW+DQ+VL+VBMI


| Method    | Mean     | Error     | StdDev    |
|---------- |---------:|----------:|----------:|
| Vector256 | 1.411 us | 0.0029 us | 0.0028 us |
| Vector512 | 1.293 us | 0.0082 us | 0.0077 us |
| Vector    | 1.092 us | 0.0101 us | 0.0095 us |
| Basic     | 3.210 us | 0.0201 us | 0.0188 us |


(中文)
## 向量演示

我们测试了三种使用Vector的方法，测试其性能表现：
- 从Vector512自动回退到Vector256的计算；
- 使用Vector256的直接计算；
- 使用Vector\<long>的自动向量化。

### 测试结果
BenchmarkDotNet v0.14.0, Windows 11 (10.0.22631.4391/23H2/2023Update/SunValley3)

AMD Ryzen 7 7840HS w/ Radeon 780M Graphics, 1 CPU, 16 logical and 8 physical cores

.NET SDK 9.0.100-rc.2.24474.11

  [Host]     : .NET 9.0.0 (9.0.24.47305), X64 RyuJIT AVX-512F+CD+BW+DQ+VL+VBMI

  DefaultJob : .NET 9.0.0 (9.0.24.47305), X64 RyuJIT AVX-512F+CD+BW+DQ+VL+VBMI


| Method    | Mean     | Error     | StdDev    |
|---------- |---------:|----------:|----------:|
| Vector256 | 1.411 us | 0.0029 us | 0.0028 us |
| Vector512 | 1.293 us | 0.0082 us | 0.0077 us |
| Vector    | 1.092 us | 0.0101 us | 0.0095 us |
| Basic     | 3.210 us | 0.0201 us | 0.0188 us |

????

- .net 新提供的 Vector512 自动降级机制，能够达到很好的性能；
- 不必像以前那样编写很长的代码,不断的判断硬件是否支持某个功能；
- 更没有必要为了简单性，使用 Vector\<T> 功能，其性能不佳； 