# Performance Testing : Kotlin 1.9 JVM / Kotlin 1.9 Native / C# .net 8.0 / Rust / Nim / V / Go

## Test Results Across Different Programming Languages 各语言测试结果

``` 
Kotlin native(1.9 Release)：
basic1 : 3721 ms.  result = 10737418235
basic2 : 5264 ms.  result = 10737418235
basic3 : 6608 ms.  result = 96325378986185
basic4 : 8030 ms.  result = 277100552390645
basic5 : 9324 ms.  result = 277100552390645
basic6 : 11194 ms.  result = 19258493759858945
basic7 : 13346 ms.  result = 2344359978479800842

Kotlin JVM(Kotlin 2.0.10, JDK 1.8):
basic1 : 2446 ms.  result = 10737418235
basic2 : 4846 ms.  result = 10737418235
basic3 : 4609 ms.  result = 96325378986185
basic4 : 5830 ms.  result = 277100552390645
basic5 : 7284 ms.  result = 277100552390645
basic6 : 8584 ms.  result = 19258493759858945
basic7 : 9943 ms.  result = 2344359978479800842

C# .net 8.0 AOT Release:
basic1 : 1989 ms.  result = 10737418235
basic2 : 3205 ms.  result = 10737418235
basic3 : 4272 ms.  result = 96325378986185
basic4 : 5604 ms.  result = 277100552390645
basic5 : 6955 ms.  result = 277100552390645
basic6 : 8579 ms.  result = 19258493759858945
basic7 : 9852 ms.  result = -2325101506203746303

Nim 2.0.8 
Note: The tests only covered cases where the parameter is 1, and range checks were disabled (-x:off).
注意：仅仅测试了 参数是 1 的情况，且关闭了范围检查（-x:off）
nim c -d:release -x:off  -r "LongConverterTest.nim"
basic: time = 2.209 result = 10737418235

Rust Release
cargo.exe test --release  --package RustDemos --test LongConverterTest -- tests::performance_test --exact --show-output

basic1: 2779 ms. result = 10737418235
basic2: 3563 ms. result = 10737418235
basic3: 4470 ms. result = 96325378986185
basic4: 5332 ms. result = 277100552390645
basic5: 6189 ms. result = 277100552390645
basic6: 7299 ms. result = 19258493759858945
basic7: 8158 ms. result = 2344360008544571914

V release
v  -prod  run src\LongConverter.v

basic1 : 1.336s ms.  result = 10737418235
basic2 : 2.274s ms.  result = 10737418235
basic3 : 3.571s ms.  result = 96325378986185
basic4 : 4.748s ms.  result = 277100552390645
basic5 : 6.881s ms.  result = 277100552390645
basic6 : 9.826s ms.  result = 19258493759858945
basic7 : 10.438s ms.  result = 2344360008544571914

go
basic1 : 3094 ms.  result = 10737418235
basic2 : 4594 ms.  result = 10737418235
basic3 : 6846 ms.  result = 96325378986185
basic4 : 8286 ms.  result = 277100552390645
basic5 : 10577 ms.  result = 277100552390645
basic6 : 12024 ms.  result = 19258493759858945
basic7 : 12278 ms.  result = 2344360008544571914

```

## Test Environment 测试环境
AMD Ryzen 7 7840HS w
Windows 11 Home RAM:16.0 GB
Release 

## Summary
This test focuses on executing basic method calls and array manipulations, 
deliberately avoiding the influence of factors such as third-party libraries, I/O operations, large memory consumption, 
virtual methods in object-oriented programming, interfaces, or aggressive optimizations like manual stack allocation and inlining. 
The goal is to evaluate the performance of typical applications written in a straightforward manner.
There's a saying: "Don't optimize prematurely." In large applications, optimization is typically only applied to critical processes. 
Therefore, the quality of the code generated by the compiler becomes crucial.

- The performance of Rust, Nim, Kotlin Native, and Go is not as impressive as expected, especially for Rust.
- Platforms like C# and Kotlin JVM have benefited from years of optimization, resulting in surprisingly good performance.
- The V language, similar to Nim, leverages the optimization techniques of C compilers. However, Nim appears to be either too conservative or insufficiently optimized, as its standard release build takes as long as 39 seconds.
- C# stands out as the most well-balanced, offering a simple programming model with solid performance and a mature ecosystem. The V language also shows strong performance with simplicity, but its reliance on C compilers presents a slightly higher barrier to entry.

## 总结
本测试执行很普通的方法调用和数组处理，避免出现第三方库质量、IO、太大内存消耗、面向对象的虚方法、接口等影响，也避免使用任何特别的优化，例如主动的栈分配，内联等，因为我们的测试目的是：普通的应用程序在正常编写的情况下，其性能如何。我们知道有个谚语：不要提前优化，当你编写了一个庞大的应用程序完毕后，除非特别重要的流程我们通常是不会优化的，但此时编程语言的编译器产生的软件质量就很关键了。

- Rust、Nim、Kotlin native、go 其性能并不是我们想象的那样好，特别是 Rust；
- C#, Kotlin JVM 这样的编程平台，因为有足够长的时间进行优化技术的沉淀，其性能实际是很好的；
- v 语言虽然和Nim语言的原理一样，都是利用 C 语言的编译优化技术沉淀，但可能Nim语言过于保守或优化不够，普通的release耗时长达39秒；
- 表现最均衡的是C#，编程模型简单但性能不差，关键是生态比较成熟，其次是 v 语言，编程也很简单性能很好，但生态依赖C，入门门槛稍高；