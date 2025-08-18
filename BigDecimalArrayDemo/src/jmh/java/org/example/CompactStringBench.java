package org.example;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
public class CompactStringBench {

    // 长度参数：覆盖你窗口边界附近
    @Param({"1","3","4","7","8","11","12","15","16","19","20","27","28","35"})
    public int len;

    // 数据类型：ASCII | UTF16 | MIX  (混合：前半 ASCII，后半中文)
    @Param({"ASCII", "UTF16", "MIX"})
    public String kind;

    // 每个基准方法循环使用的样本池（幂次掩码便于无取模滚动）
    private static final int POOL_SIZE = 2048;
    private static final int MASK = POOL_SIZE - 1;

    private String[] inputs;       // 原始字符串池
    private Object[] encodedPool;  // 预编码对象池（仅 toString-only 用）
    private int idx;

    @Setup(Level.Trial)
    public void setup() {
        inputs = new String[POOL_SIZE];
        final String asciiSrc = "abcdefghijklmnopqrstuvwxyz0123456789~!@#$%^&*()-_=+[]{}|;:,./?";
        final String cnSrc    = "你要插内存栅栏防止发布前被读到更要保证数组不会再被修改黑色的点赞一家人龙😀";

        for (int i = 0; i < POOL_SIZE; i++) {
            switch (kind) {
                case "ASCII": {
                    StringBuilder sb = new StringBuilder(len);
                    for (int k = 0; k < len; k++) {
                        sb.append(asciiSrc.charAt((i + k) % asciiSrc.length()));
                    }
                    inputs[i] = sb.toString();
                    break;
                }
                case "UTF16": {
                    StringBuilder sb = new StringBuilder(len);
                    for (int k = 0; k < len; k++) {
                        sb.append(cnSrc.charAt((i + k) % cnSrc.length()));
                    }
                    inputs[i] = sb.toString();
                    break;
                }
                default: { // MIX
                    int asciiLen = Math.max(0, len / 2);
                    int cnLen = Math.max(0, len - asciiLen);
                    StringBuilder sb = new StringBuilder(len);
                    for (int k = 0; k < asciiLen; k++) {
                        sb.append(asciiSrc.charAt((i + k) % asciiSrc.length()));
                    }
                    for (int k = 0; k < cnLen; k++) {
                        sb.append(cnSrc.charAt((i + k) % cnSrc.length()));
                    }
                    inputs[i] = sb.toString();
                }
            }
        }

        // 预编码池：仅 toString-only 基准用，避免把 encode 成本混入
        encodedPool = new Object[POOL_SIZE];
        for (int i = 0; i < POOL_SIZE; i++) {
            encodedPool[i] = CompactStringUtils.INSTANCE.encode(inputs[i]);
        }
        idx = 0;
    }

    // ============ 基准项 ============

    /** 仅 encode：测压缩写入吞吐（不做 toString） */
    @Benchmark
    public Object encode_only(Blackhole bh) {
        String s = inputs[(idx++) & MASK];
        Object o = CompactStringUtils.INSTANCE.encode(s);
        bh.consume(o);
        return o;
    }

    /** encode 后立即 toString：测端到端往返 */
    @Benchmark
    public String encode_then_toString(Blackhole bh) {
        String s = inputs[(idx++) & MASK];
        Object o = CompactStringUtils.INSTANCE.encode(s);
        String r = o.toString();
        bh.consume(r);
        return r;
    }

    /** 仅 toString：对预先 encode 好的对象解包（不含 encode 成本） */
    @Benchmark
    public String toString_only(Blackhole bh) {
        Object o = encodedPool[(idx++) & MASK];
        String r = o.toString();
        bh.consume(r);
        return r;
    }

    // 参考基线：直接创建/复制常规 String（可选）
    @Benchmark
    public String baseline_string_new(Blackhole bh) {
        String s = inputs[(idx++) & MASK];
        // 模拟“新建副本”的开销（避免 COW/常量池干扰）
        String r = new String(s.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        bh.consume(r);
        return r;
    }
}
