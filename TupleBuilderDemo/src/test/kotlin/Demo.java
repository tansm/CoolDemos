
import com.example.orm.ITuple;
import java.lang.Class;
import com.example.orm.TupleUtils;

final class Tuple_IIIIIIIO implements ITuple {
    public int item0;
    public int item1;
    public int item2;
    public int item3;
    public int item4;
    public int item5;
    public int item6;
    public com.example.orm.ITuple rest;

    public Tuple_IIIIIIIO() {}

    @Override
    public int getSize() {
        int size = 7;
        if (this.rest != null) {
            size += this.rest.getSize();
        }
        return size;
    }

    @Override
    public Class<?> getFieldType(int index) {
        if (index < 0) {
            return TupleUtils.throwIndexOutOfBounds(index);
        }
        switch (index) {
            case 0: return int.class;
            case 1: return int.class;
            case 2: return int.class;
            case 3: return int.class;
            case 4: return int.class;
            case 5: return int.class;
            case 6: return int.class;
            default:
                if (this.rest == null) {
                    TupleUtils.throwIllegalStateException(index);
                }
                return this.rest.getFieldType(index - 7);
        }
    }

    @Override
    public Object getItem(int index) {
        if (index < 0) {
            return TupleUtils.throwIndexOutOfBounds(index);
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
                    return TupleUtils.throwIndexOutOfBounds(index);
                }
                return this.rest.getItem(index - 7);
        }
    }

    @Override
    public int getInt(int index) {
        if (index < 0) {
            return TupleUtils.throwIndexOutOfBounds(index);
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
                    TupleUtils.throwIllegalStateException(index);
                }
                return this.rest.getInt(index - 7);
        }
    }

    @Override
    public long getLong(int index) {
        if (index < 0) {
            return TupleUtils.throwIndexOutOfBounds(index);
        }
        switch (index) {
            case 0,1,2,3,4,5,6: TupleUtils.throwClassCastException(index, "Long");
            default:
                if (this.rest == null) {
                    TupleUtils.throwIllegalStateException(index);
                }
                return this.rest.getLong(index - 7);
        }
    }

    @Override
    public boolean getBoolean(int index) {
        if (index < 0) {
            return TupleUtils.throwIndexOutOfBounds(index);
        }
        switch (index) {
            case 0,1,2,3,4,5,6: TupleUtils.throwClassCastException(index, "Boolean");
            default:
                if (this.rest == null) {
                    TupleUtils.throwIllegalStateException(index);
                }
                return this.rest.getBoolean(index - 7);
        }
    }

    @Override
    public byte getByte(int index) {
        if (index < 0) {
            return TupleUtils.throwIndexOutOfBounds(index);
        }
        switch (index) {
            case 0,1,2,3,4,5,6: TupleUtils.throwClassCastException(index, "Byte");
            default:
                if (this.rest == null) {
                    TupleUtils.throwIllegalStateException(index);
                }
                return this.rest.getByte(index - 7);
        }
    }

    @Override
    public short getShort(int index) {
        if (index < 0) {
            return TupleUtils.throwIndexOutOfBounds(index);
        }
        switch (index) {
            case 0,1,2,3,4,5,6: TupleUtils.throwClassCastException(index, "Short");
            default:
                if (this.rest == null) {
                    TupleUtils.throwIllegalStateException(index);
                }
                return this.rest.getShort(index - 7);
        }
    }

    @Override
    public char getChar(int index) {
        if (index < 0) {
            return TupleUtils.throwIndexOutOfBounds(index);
        }
        switch (index) {
            case 0,1,2,3,4,5,6: TupleUtils.throwClassCastException(index, "Char");
            default:
                if (this.rest == null) {
                    TupleUtils.throwIllegalStateException(index);
                }
                return this.rest.getChar(index - 7);
        }
    }

    @Override
    public float getFloat(int index) {
        if (index < 0) {
            return TupleUtils.throwIndexOutOfBounds(index);
        }
        switch (index) {
            case 0,1,2,3,4,5,6: TupleUtils.throwClassCastException(index, "Float");
            default:
                if (this.rest == null) {
                    TupleUtils.throwIllegalStateException(index);
                }
                return this.rest.getFloat(index - 7);
        }
    }

    @Override
    public double getDouble(int index) {
        if (index < 0) {
            return TupleUtils.throwIndexOutOfBounds(index);
        }
        switch (index) {
            case 0,1,2,3,4,5,6: TupleUtils.throwClassCastException(index, "Double");
            default:
                if (this.rest == null) {
                    TupleUtils.throwIllegalStateException(index);
                }
                return this.rest.getDouble(index - 7);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Tuple_IIIIIIIO(");
        sb.append("item0=").append(this.item0);
        sb.append(", ");
        sb.append("item1=").append(this.item1);
        sb.append(", ");
        sb.append("item2=").append(this.item2);
        sb.append(", ");
        sb.append("item3=").append(this.item3);
        sb.append(", ");
        sb.append("item4=").append(this.item4);
        sb.append(", ");
        sb.append("item5=").append(this.item5);
        sb.append(", ");
        sb.append("item6=").append(this.item6);
        sb.append(", ");
        sb.append("rest=").append(this.rest);
        sb.append(")");
        return sb.toString();
    }
}