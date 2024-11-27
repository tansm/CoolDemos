package main

import (
	"fmt"
	"math"
	"time"
)

// ILongConverter 接口
type ILongConverter interface {
    GetMax() uint64
    ToX(y []uint32) uint64
}

// MemberMap 结构体
type MemberMap struct {
    Carry        uint64
    DimPosition  int
    MinValue     uint32
    MaxValue     uint32
}

// ToX 方法
func (m MemberMap) ToX(y []uint32) uint64 {
    return m.Carry * uint64((y[m.DimPosition] - m.MinValue))
}

// Step 方法
func (m MemberMap) Step() uint64 {
    return uint64(m.MaxValue) - uint64(m.MinValue) + 1
}

// Max 函数
func Max(att []uint32) uint32 {
    max := att[0]
    for _, num := range att[1:] {
        if num > max {
            max = num
        }
    }
    return max
}

// Min 函数
func Min(att []uint32) uint32 {
    min := att[0]
    for _, num := range att[1:] {
        if num < min {
            min = num
        }
    }
    return min
}

// NewMemberMap 函数
func NewMemberMap(dimPosition int, members []uint32, carry uint64) MemberMap {
    return MemberMap{
        Carry:        carry,
        DimPosition:  dimPosition,
        MinValue:     Min(members),
        MaxValue:     Max(members),
    }
}

// LongConverter 结构体
type LongConverter struct {
    FixedMembers []uint32
    Mappings     []MemberMap
}

// ToX 方法
func (l LongConverter) ToX(y []uint32) uint64 {
    var result uint64 = 0
    for _, mapping := range l.Mappings {
        result += mapping.ToX(y)
    }
    return result
}

// NewLongConverter 函数
func NewLongConverter(dimensionInfo [][]uint32) LongConverter {
    list := make([]MemberMap, 0, len(dimensionInfo))
    carry := uint64(1)
    fixedMembers := make([]uint32, len(dimensionInfo))

    for i, dim := range dimensionInfo {
        switch len(dim) {
        case 0:
            // 空处理
        case 1:
            fixedMembers[i] = dim[0]
        default:
            m := NewMemberMap(i, dim, carry)
            if m.Step() == 0 {
                fixedMembers[i] = dim[0]
            } else {
                carry *= m.Step()
                list = append(list, m)
            }
        }
    }

    return LongConverter{
        FixedMembers: fixedMembers,
        Mappings:     list,
    }
}

// GetDimensionInfo 函数
func GetDimensionInfo(size int) [][]uint32 {
    var temp [][]uint32
    switch size {
    case 1:
        temp = [][]uint32{
            {900},
            {0, 1, 9, 5},
            {900}, {900}, {900},
            {900}, {900}, {900}, {900}, {900},
            {900},
        }
    case 2:
        temp = [][]uint32{
            {900},
            {0, 1, 9, 5},
            {900},
            {900, 832},
            {900},
            {900}, {900}, {900}, {900}, {900},
            {900},
        }
    case 3:
        temp = [][]uint32{
            {900},
            {0, 1, 9, 5},
            {900},
            {900, 832},
            {3, 6, 8, 4, 2, 2, 44, 56, 67, 32, 123},
            {900}, {900}, {900}, {900}, {900},
            {900},
        }
    case 4:
        temp = [][]uint32{
            {900},
            {0, 1, 9, 5},
            {900},
            {900, 832},
            {3, 6, 8, 4, 2, 2, 44, 56, 67, 32, 123},
            {900},
            {900},
            {10000000, 10000002, 10000001},
            {900},
            {900},
            {900},
        }
    case 5:
        temp = [][]uint32{
            {900},
            {0, 1, 9, 5},
            {900},
            {900, 832},
            {3, 6, 8, 4, 2, 2, 44, 56, 67, 32, 123},
            {900},
            {900},
            {10000000, 10000002, 10000001},
            {900},
            {4, 9, 10},
            {900},
        }
    case 6:
        temp = [][]uint32{
            {900},
            {0, 1, 9, 5},
            {900},
            {900, 832},
            {3, 6, 8, 4, 2, 2, 44, 56, 67, 32, 123},
            {900},
            {900},
            {10000000, 10000002, 10000001},
            {900},
            {4, 9, 10},
            {9, 4},
        }
    case 7:
        temp = [][]uint32{
            {8, 7},
            {0, 1, 9, 5},
            {900},
            {900, 832},
            {3, 6, 8, 4, 2, 2, 44, 56, 67, 32, 123},
            {900},
            {900},
            {10000000, 10000002, 10000001},
            {900},
            {4, 9, 10},
            {9, 4},
        }
    default:
        temp = [][]uint32{{1}}
    }

    return temp
}

// PerformanceTestCore 函数
func PerformanceTestCore(name string, target LongConverter) {
    key := []uint32{9, 5, 900, 832, 67, 800, 1000000, 10000001, 3, 4, 9}
    var x uint64 = 0
    start := time.Now()

    for i := 0; i < math.MaxInt32; i++ {
        key[0] = uint32(i)
        x += target.ToX(key)
    }

    elapsed := time.Since(start)
    fmt.Printf("%s : %v ms.  result = %d\n", name, elapsed.Milliseconds(), x)
}

// PerformanceTest 函数
func PerformanceTest() {
    for i := 1; i < 8; i++ {
        PerformanceTestCore(fmt.Sprintf("basic%d", i), NewLongConverter(GetDimensionInfo(i)))
    }
}

func main() {
    PerformanceTest()
}