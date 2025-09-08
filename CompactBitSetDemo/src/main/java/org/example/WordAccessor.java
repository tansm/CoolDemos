package org.example;

import java.util.Arrays;

/**
 * 自定义接口：由宿主实现，Handle通过它访问word。
 */
public interface WordAccessor<T> {
    /**
     * 获取指定wordIndex的long值。如果wordIndex越界，返回0L。
     */
    long getWord(T instance, int wordIndex);

    /**
     * 设置指定wordIndex的long值。如果需要扩容，应在ensureCapacity中处理。
     * 如果value == 0 且 wordIndex 在边界，可选择不存储（但不强制）。
     */
    void setWord(T instance, int wordIndex, long value);

    /**
     * 确保能支持至少minWordCount个word。实现时可扩容heap数组。
     */
    void ensureCapacity(T instance, int minWordCount);

    /**
     * 获取当前word总数（inline + heap），用于length/cardinality等。
     */
    int getWordCount(T instance);

    /**
     * 清理尾部0 word，减少内存（可选实现，默认空）。
     */
    default void trim(T instance) {
        // 默认不实现，宿主可override
    }
}