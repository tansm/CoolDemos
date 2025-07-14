
import com.example.orm.AbstractTuple;
import org.jetbrains.annotations.NotNull;

import java.lang.Class;

// 这是一个最终生成的代码， 包含 int, object,bool 等字段类型，且包含 rest 字段。
final class Tuple_IOBIOBIR extends AbstractTuple {
    public int item0;
    public Object item1;
    public boolean item2;
    public int item3;
    public Object item4;
    public boolean item5;
    public int item6;
    public com.example.orm.AbstractTuple rest;

    public Tuple_IOBIOBIR() {}

    @Override
    public int getDirectSize() {
        return 7;
    }

    @Override
    public AbstractTuple getRest() {
        return rest;
    }

    @Override
    public void setRest(AbstractTuple value) {
        rest = value;
    }

    @Override
    public boolean getHasRestField() {
        return true;
    }

    @NotNull
    @Override
    public Class<?> getFieldType(int index) {
        if (index < 0) {
            return throwIndexOutOfBounds(index);
        }
        switch (index) {
            case 0: return int.class;
            case 1: return java.lang.Object.class;
            case 2: return boolean.class;
            case 3: return int.class;
            case 4: return java.lang.Object.class;
            case 5: return boolean.class;
            case 6: return int.class;
            default:
                if (this.rest == null) {
                    throwIllegalStateException(index);
                }
                return this.rest.getFieldType(index - 7);
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
            case 3: return this.item3;
            case 4: return this.item4;
            case 5: return this.item5;
            case 6: return this.item6;
            default:
                if (this.rest == null) {
                    return throwIndexOutOfBounds(index);
                }
                return this.rest.getItem(index - 7);
        }
    }

    @Override
    public void setItem(int index, Object value) {
        if (index < 0) {
            throwIndexOutOfBounds(index);
            return;
        }
        switch (index) {
            case 0: this.item0 = ((Integer)value).intValue(); return;
            case 1: this.item1 = value; return;
            case 2: this.item2 = ((Boolean)value).booleanValue(); return;
            case 3: this.item3 = ((Integer)value).intValue(); return;
            case 4: this.item4 = value; return;
            case 5: this.item5 = ((Boolean)value).booleanValue(); return;
            case 6: this.item6 = ((Integer)value).intValue(); return;
            default:
                if (this.rest == null) {
                    throwIllegalStateException(index);
                }
                this.rest.setItem(index - 7, value);
        }
    }

    @Override
    public int getInt(int index) {
        switch (index) {
            case 0: return this.item0;
            case 3: return this.item3;
            case 6: return this.item6;
            default: return super.getInt(index);
        }
    }

    @Override
    public boolean getBoolean(int index) {
        switch (index) {
            case 2: return this.item2;
            case 5: return this.item5;
            default: return super.getBoolean(index);
        }
    }

    @Override
    public void setInt(int index, int value) {
        switch (index) {
            case 0: this.item0 = value; return;
            case 3: this.item3 = value; return;
            case 6: this.item6 = value; return;
            default: super.setInt(index, value);
        }
    }

    @Override
    public void setBoolean(int index, boolean value) {
        switch (index) {
            case 2: this.item2 = value; return;
            case 5: this.item5 = value; return;
            default: super.setBoolean(index, value);
        }
    }

}