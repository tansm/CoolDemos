package org.example;

import org.junit.Before;
import org.junit.Test;
import java.math.BigDecimal;
import static org.junit.Assert.*;

public class BigDecimalArrayTest {
    private SaleOrder order;

    @Before
    public void setUp() {
        order = new SaleOrder();
    }

    @Test
    public void testGetSize() {
        assertEquals(60, order.getDecimalFieldSize());
    }

    @Test
    public void testDefaultNull() {
        // 默认初始化为 null
        assertNull(order.getItem0());
        assertNull(order.getItem59()); // 最后一个字段
    }

    @Test
    public void testSetAndGetNull() {
        order.setItem0(null);
        assertNull(order.getItem0());
        // 验证缓存不影响 null
        assertNull(order.getItem0());
    }

    @Test
    public void testSetAndGetSmallNumber() {
        BigDecimal value = new BigDecimal("123.45");
        order.setItem1(value);
        BigDecimal result = order.getItem1();
        assertEquals(value, result);

        order.setItem1(BigDecimal.ONE);
        assertSame(BigDecimal.ONE, order.getItem1()); // 验证缓存
    }

    @Test
    public void testSetAndGetLargeNumber() {
        BigDecimal value = new BigDecimal("12345678901234567890.123");
        order.setItem2(value);
        BigDecimal result = order.getItem2();
        assertEquals(value, result);
        assertSame(value, order.getItem2()); // 验证不创建新对象
    }

    @Test
    public void testSetAndGetZero() {
        BigDecimal value = BigDecimal.ZERO;
        order.setItem3(value);
        BigDecimal result = order.getItem3();
        assertEquals(BigDecimal.ZERO, result);
        assertSame(BigDecimal.ZERO, result); // 验证池化
    }

    @Test
    public void testSetAndGetNegativeScale() {
        BigDecimal value = new BigDecimal("1234500"); // scale = 0
        BigDecimal equivalent = BigDecimal.valueOf(12345, -2); // 12345 * 10^2
        order.setItem4(equivalent);
        BigDecimal result = order.getItem4();
        assertEquals(0,value.compareTo(result));
        assertEquals(-2, result.scale());
    }

    @Test
    public void testSetAndGetLargeScale() {
        BigDecimal value = BigDecimal.valueOf(123, 128); // scale 超出 byte 范围
        order.setItem5(value);
        BigDecimal result = order.getItem5();
        assertEquals(value, result);
        assertSame(result, order.getItem5()); // 验证缓存
    }

    @Test
    public void testSetAndGetLongMinValue() {
        BigDecimal value = BigDecimal.valueOf(Long.MIN_VALUE, 0);
        order.setItem59(value);
        BigDecimal result = order.getItem59();
        assertEquals(value, result);
        assertSame(result, order.getItem59()); // 验证缓存
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidIndexNegative() {
        order.getItem(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidIndexTooLarge() {
        order.getItem(60);
    }

    @Test
    public void testIntegrationMultipleFields() {
        BigDecimal smallValue = new BigDecimal("12.34");
        BigDecimal largeValue = new BigDecimal("12345678901234567890.123");
        BigDecimal zeroValue = BigDecimal.ZERO;
        BigDecimal negativeScaleValue = BigDecimal.valueOf(12345, -2);
        BigDecimal largeScaleValue = BigDecimal.valueOf(123, 128);

        // 设置多个字段
        order.setItem0(null);
        order.setItem1(smallValue);
        order.setItem2(largeValue);
        order.setItem3(zeroValue);
        order.setItem4(negativeScaleValue);
        order.setItem5(largeScaleValue);
        order.setItem59(smallValue); // 测试最后一个字段

        // 验证值
        assertNull(order.getItem0());
        assertEquals(smallValue, order.getItem1());
        assertEquals(largeValue, order.getItem2());
        assertEquals(zeroValue, order.getItem3());
        assertEquals(negativeScaleValue, order.getItem4());
        assertEquals(largeScaleValue, order.getItem5());
        assertEquals(smallValue, order.getItem59());

        // 验证缓存和池化
        assertSame(BigDecimal.ZERO, order.getItem3());
    }
}