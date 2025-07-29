package org.example;

import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Arrays;

public class BigDecimalArray {
    private static final Unsafe UNSAFE;
    private static final long INT_COMPACT_OFFSET;
    private static final long INFLATED = Long.MIN_VALUE;

    static {
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            UNSAFE = (Unsafe) unsafeField.get(null);
            INT_COMPACT_OFFSET = UNSAFE.objectFieldOffset(BigDecimal.class.getDeclaredField("intCompact"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Unsafe fields", e);
        }
    }

    private final int size;
    private final long[] intCompacts;
    private final byte[] scales;
    private volatile BigDecimal[] objects;

    public int getSize(){
        return size;
    }

    public BigDecimalArray(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Invalid array size: " + size);
        }
        this.size = size;
        this.intCompacts = new long[size];
        Arrays.fill(intCompacts, INFLATED);
        this.scales = new byte[size];
        this.objects = null; // Lazy initialization
    }

    public BigDecimal get(int index) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException("Invalid index: " + index);
        }
        long intCompact = intCompacts[index];
        if (intCompact == INFLATED) {
            return objects == null ? null : objects[index];
        }
        return BigDecimal.valueOf(intCompacts[index], scales[index]);
    }

    public void set(int index, BigDecimal value) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException("Invalid index: " + index);
        }
        if (value == null) {
            intCompacts[index] = INFLATED;
            scales[index] = 0;
            if (objects != null) {
                objects[index] = null;
            }
            return;
        }
        long intCompact = UNSAFE.getLong(value, INT_COMPACT_OFFSET);
        int scale = value.scale();

        if (intCompact == INFLATED || scale < Byte.MIN_VALUE || scale > Byte.MAX_VALUE) {
            if (objects == null) {
                synchronized (this) { // Thread-safe initialization
                    if (objects == null) { // Double-checked locking
                        objects = new BigDecimal[size];
                    }
                }
            }
            intCompacts[index] = INFLATED;
            scales[index] = 0;
            objects[index] = value;
        } else {
            intCompacts[index] = intCompact;
            scales[index] = (byte) scale;
            if (objects != null) {
                objects[index] = null;
            }
        }
    }
}