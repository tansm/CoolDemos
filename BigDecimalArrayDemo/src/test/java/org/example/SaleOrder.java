package org.example;

import java.math.BigDecimal;

public class SaleOrder {
    private static final int FIELD_COUNT = 60;
    private final BigDecimalArray decimalArray = new BigDecimalArray(FIELD_COUNT);

    // 仅仅用于测试，实际代码请不要 public 这样的方法。
    public BigDecimal getItem(int index) {
        return decimalArray.get(index);
    }

    public void setItem(int index, BigDecimal value) {
        decimalArray.set(index, value);
    }

    // 仅仅用于测试，实际代码不要有此方法。
    public int getDecimalFieldSize() {
        return decimalArray.getSize();
    }

    public BigDecimal getItem0() {
        return this.getItem(0);
    }
    public void setItem0(BigDecimal value) {
        this.setItem(0, value);
    }

    public BigDecimal getItem1() {
        return this.getItem(1);
    }
    public void setItem1(BigDecimal value) {
        this.setItem(1, value);
    }

    public BigDecimal getItem2() {
        return this.getItem(2);
    }
    public void setItem2(BigDecimal value) {
        this.setItem(2, value);
    }

    public BigDecimal getItem3() {
        return this.getItem(3);
    }
    public void setItem3(BigDecimal value) {
        this.setItem(3, value);
    }

    public BigDecimal getItem4() {
        return this.getItem(4);
    }
    public void setItem4(BigDecimal value) {
        this.setItem(4, value);
    }

    public BigDecimal getItem5() {
        return this.getItem(5);
    }
    public void setItem5(BigDecimal value) {
        this.setItem(5, value);
    }

    // ....

    public BigDecimal getItem59() {
        return this.getItem(59);
    }
    public void setItem59(BigDecimal value) {
        this.setItem(59, value);
    }
}