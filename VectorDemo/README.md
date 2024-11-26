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


(����)
## ������ʾ

���ǲ���������ʹ��Vector�ķ��������������ܱ��֣�
- ��Vector512�Զ����˵�Vector256�ļ��㣻
- ʹ��Vector256��ֱ�Ӽ��㣻
- ʹ��Vector\<long>���Զ���������

### ���Խ��
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

- .net ���ṩ�� Vector512 �Զ��������ƣ��ܹ��ﵽ�ܺõ����ܣ�
- ��������ǰ������д�ܳ��Ĵ���,���ϵ��ж�Ӳ���Ƿ�֧��ĳ�����ܣ�
- ��û�б�ҪΪ�˼��ԣ�ʹ�� Vector\<T> ���ܣ������ܲ��ѣ� 