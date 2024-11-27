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
- AVX-512 bandwidth, even when processing `Int64`, does not achieve an 8x performance boost��**3x** is more realistic.

How should we summarize these contradictory results?

- Factors such as Windows version, CPU, and .NET runtime can all impact performance. Always benchmark your code thoroughly instead of relying solely on assumptions or prior experiences.
- Were there any lessons learned from this test? It seems that using `Vector<T>` is a way that doesn't easily lead to major mistakes.

### Supplement
I added tests to measure how much aligned memory access affects performance. On my i5 CPU (which does not support AVX-512), it improved performance by approximately 25%.

| Method        | Mean     | Error     | StdDev    |
|-------------- |---------:|----------:|----------:|
| Vector        | 2.009 us | 0.0377 us | 0.0403 us |
| AlignedVector | 1.609 us | 0.0308 us | 0.0390 us |


(����)
## ������ʾ

�Ҳ���������ʹ��Vector�ķ��������������ܱ��֣�
- ��Vector512�Զ����˵�Vector256�ļ��㣻
- ʹ��Vector256��ֱ�Ӽ��㣻
- ʹ��Vector\<long>���Զ���������

### ���Խ��

������̨�����������������׼���ԣ���һ̨�������֧�� AVX-512.
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

`�ƺ�` �ܹ��ó����½��ۣ�
- .net ���ṩ�� Vector512 �Զ��������ƣ��ܹ��ﵽ�ܺõ����ܣ�
- ��������ǰ������д�ܳ��Ĵ���,���ϵ��ж�Ӳ���Ƿ�֧��ĳ�����ܣ�
- ��û�б�ҪΪ�˼��ԣ�ʹ�� Vector\<T> ���ܣ������ܲ��ѣ� 

���ǣ�����...

������������һ̨��������»���CPU�Ƚ��£�֧�� AVX-512������Vector512�ᰴ��ʵ�ʵ�ָ��ִ�У����ý�������
Vector256�����У�����û��AVX-512�ļ�⣬����ʵ�ʰ��� 256λ ��ʽִ�С�

Vector Ӧ�û��Զ����CPU֧�� AVX-512������ʹ�� AVX-512 ָ��ִ�С�

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

����Ĳ��Խ�����ƺ��ó���ȫ��ͬ�Ľ��ۣ�
- ֱ��ʹ�� Vector\<T>���������Ż�����ˣ�
- ��Ҫ���� 512 λ���� 256 λ���ܵ���������ʵ�ϣ�����������΢����΢��
- ��Ҫ���� AVX-512 ��������� Int64 �� 8���������ܾ��� 8 ����������ʵ��ֻ��3����

��ô�������ȫ�෴�Ľ��ۣ�������ܽ��أ� 

- Windows��CPU��.net �İ汾����ͬ��Ӱ������غܶ࣬�����κδ��뻹��Ҫ������׼���ԣ���Ҫ̫���ž��飻
- ��β������ܽᾭ���� �ƺ�ʹ�� Vector\<T> �Ǹ���̫�����ķ�ʽ��

### ����

�������˲��ԣ�����ʹ�ö��뷽ʽ������Ӱ���ж�����ҵ� i5 CPU�����ϣ���֧��AVX-512)�������25%���ҡ�

| Method        | Mean     | Error     | StdDev    |
|-------------- |---------:|----------:|----------:|
| Vector        | 2.009 us | 0.0377 us | 0.0403 us |
| AlignedVector | 1.609 us | 0.0308 us | 0.0390 us |