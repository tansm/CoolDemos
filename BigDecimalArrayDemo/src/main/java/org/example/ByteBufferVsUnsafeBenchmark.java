package org.example;

import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

public class ByteBufferVsUnsafeBenchmark {
    private static final Unsafe UNSAFE;

    static {
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            UNSAFE = (Unsafe) unsafeField.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Unsafe", e);
        }
    }

    public static void main(String[] args) {
        byte[] array = new byte[8 * 1_000_000];
        for (int i = 0; i < array.length; i += 8) {
            ByteBuffer.wrap(array).putLong(i, i);
        }
        ByteBuffer bb = ByteBuffer.wrap(array);

        // 预热 JIT
        for (int i = 0; i < 100_000; i++) {
            bb.position(0);
            for (int j = 0; j < 1000; j++) {
                bb.getLong();
            }
        }
        long baseOffset = UNSAFE.arrayBaseOffset(byte[].class);
        for (int i = 0; i < 100_000; i++) {
            for (int j = 0; j < 1000; j++) {
                UNSAFE.getLong(array, baseOffset + (long) j * 8);
            }
        }

        // 测试 ByteBuffer
        long start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            //bb.position(0);
            for (int j = 0; j < 1_000_000; j++) {
                bb.getLong(j * 8);
            }
        }
        long bbTime = System.nanoTime() - start;
        System.out.println("ByteBuffer.getLong: " + bbTime / 1_000_000.0 + " ms");

        // 测试 Unsafe
        start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            for (int j = 0; j < 1_000_000; j++) {
                UNSAFE.getLong(array, baseOffset + (long) j * 8);
            }
        }
        long unsafeTime = System.nanoTime() - start;
        System.out.println("Unsafe.getLong: " + unsafeTime / 1_000_000.0 + " ms");
    }
}
