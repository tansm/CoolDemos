//package com.mycompany.generated.tuples;

import com.example.orm.AbstractTuple;
import org.jetbrains.annotations.Nullable;

import java.lang.Class;

final class Tuple_OBL extends AbstractTuple {
    public Object item0;
    public boolean item1;
    public long item2;

    public Tuple_OBL() {}

    @Override
    public int getDirectSize() {
        int size = 3;
        return size;
    }

    @Override
    public boolean getHasRestField() {
        return super.getHasRestField();
    }

    @Override
    public Class<?> getFieldType(int index) {
        if (index < 0) {
            return throwIndexOutOfBounds(index);
        }
        switch (index) {
            case 0: return java.lang.Object.class;
            case 1: return boolean.class;
            case 2: return long.class;
            default: return throwIndexOutOfBounds(index);
        }
    }

    @Override
    public Object getItem(int index) {
        if (index < 0) {
            return throwIndexOutOfBounds(index);
        }
        switch (index) {
            case 0: return this.item0;
            case 1: return this.item1;
            case 2: return this.item2;
            default: return throwIndexOutOfBounds(index);
        }
    }

    @Override
    public long getLong(int index) {
        if (index < 0) {
            return throwIndexOutOfBounds(index);
        }
        switch (index) {
            case 2: return this.item2;
            case 0,1: throwClassCastException(index, "Long");
            default: return throwIndexOutOfBounds(index);
        }
    }

    @Override
    public boolean getBoolean(int index) {
        if (index < 0) {
            return throwIndexOutOfBounds(index);
        }
        switch (index) {
            case 1: return this.item1;
            case 0: throwClassCastException(index, "Boolean");
            case 2: throwClassCastException(index, "Boolean");
            default: return throwIndexOutOfBounds(index);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Tuple_OBL(");
        sb.append("item0=").append(this.item0);
        sb.append(", ");
        sb.append("item1=").append(this.item1);
        sb.append(", ");
        sb.append("item2=").append(this.item2);
        sb.append(")");
        return sb.toString();
    }
}