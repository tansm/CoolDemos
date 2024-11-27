import math
import time

interface ILongConverter[Y] {
	get_max u64
	to_x(y Y) u64
	/**
     * 最大的可能值，不包括此值。
	*/
}

struct MemberMap {
	carry		u64
	dim_position int
	min_value	u32
	max_value	u32
}

fn (this MemberMap) to_x(y []u32) u64 {
	return this.carry * (y[this.dim_position] - this.min_value)
}

fn (this MemberMap) step() u64 {
	return u64(this.max_value) - u64(this.min_value) + 1
}

fn max(att []u32) u32 {
    mut max := att[0]
    for num in att[1..] {
        if num > max {
            max = num
        }
    }
    return max
}

fn min(att []u32) u32 {
    mut min := att[0]
    for num in att[1..] {
        if num < min {
            min = num
        }
    }
    return min
}

fn new_member_map(dimPosition int, members []u32, carry u64) MemberMap {
	return MemberMap{
		carry: carry
		dim_position: dimPosition
		min_value: min(members)
		max_value: max(members)
	}
}

struct LongConverter {
	fixed_members []u32
	mappings      []MemberMap
}

fn (this LongConverter) to_x(y []u32) u64{
	mut result := u64(0)

	for mapping in this.mappings{
		result += mapping.to_x(y)
	}

	return result
}

fn new_long_converter(dimensionInfo [][]u32) LongConverter {
	mut list := []MemberMap{cap: dimensionInfo.len}
	mut carry := u64(1)
	mut fixed_members := []u32{len: dimensionInfo.len}

	for i,dim in dimensionInfo {
		match dim.len {
			0 {}
			1 {
				fixed_members[i] = dim[0]
			}
			else {
				mut m := new_member_map(i, dim, carry)
				if m.step() == 0{
					fixed_members[i] = dim[0]
				}else{
					carry *= m.step()
					list << m
				}
			}
		}
	}

	return LongConverter{
		fixed_members: fixed_members
		mappings: list
	}
}



fn get_dimension_info(size int) [][]u32 {
	temp := match size{
		1 {
			[
			[900],
            [0, 1, 9, 5],    //min = 0, max: 9
            [900],[900],[900],

            [900],[900],[900],[900],[900],

            [900],
            ]
		}
		2 {
			[
				[900],
                [0, 1, 9, 5],    //min = 0, max: 9
                [900],
                [900, 832],    //min = 832,max = 900
                [900],

                [900],[900],[900],[900],[900],

                [900],
			]
		}
		3 {
			[
				[900],
                [0, 1, 9, 5],    //min = 0, max: 9
                [900],
                [900, 832],    //min = 832,max = 900
                [3, 6, 8, 4, 2, 2, 44, 56, 67, 32, 123], //min=2, max=123

                [900],[900],[900],[900],[900],

                [900],
			]
		}
		4 {
			[
				[900],
                [0, 1, 9, 5],    //min = 0, max: 9
                [900],
                [900, 832],    //min = 832,max = 900
                [3, 6, 8, 4, 2, 2, 44, 56, 67, 32, 123], //min=2, max=123

                [900],
                [900],
                [10000000, 10000002, 10000001], //min=10000000, max=10000002
                [900],
                [900],

                [900],
			]
		}
		5 {
			[
				[900],
                [0, 1, 9, 5],    //min = 0, max: 9
                [900],
                [900, 832],    //min = 832,max = 900
                [3, 6, 8, 4, 2, 2, 44, 56, 67, 32, 123], //min=2, max=123

                [900],
                [900],
                [10000000, 10000002, 10000001], //min=10000000, max=10000002
                [900],
                [4, 9, 10],     //min=4, max =10

                [900],
			]
		}
		6{
			[
				[900],
                [0, 1, 9, 5],    //min = 0, max: 9
                [900],
                [900, 832],    //min = 832,max = 900
                [3, 6, 8, 4, 2, 2, 44, 56, 67, 32, 123], //min=2, max=123

                [900],
                [900],
                [10000000, 10000002, 10000001], //min=10000000, max=10000002
                [900],
                [4, 9, 10],     //min=4, max =10

                [9, 4],        //min=4, max = 9
			]
		}
		7 {
			[
				[8,7],
                [0, 1, 9, 5],    //min = 0, max: 9
                [900],
                [900, 832],    //min = 832,max = 900
                [3, 6, 8, 4, 2, 2, 44, 56, 67, 32, 123], //min=2, max=123

                [900],
                [900],
                [1000_0000, 1000_0002, 1000_0001], //min=10000000, max=10000002
                [900],
                [4, 9, 10],     //min=4, max =10

                [9, 4],        //min=4, max = 9
			]
		}
		else { [ [1] ] }
	}

	mut jagged_u32 := [][]u32{}

    for subarray in temp {
        mut new_subarray := []u32{}
        for num in subarray {
            new_subarray << u32(num)
        }
        jagged_u32 << new_subarray
    }
	return jagged_u32
}

fn performance_test_core(name string, target LongConverter) {
	mut key := [u32(9), 5, 900, 832, 67, 800, 100_0000, 1000_0001, 3, 4, 9]
	mut x := u64(0)
	start := time.now()

	for i := 0; i < math.max_i32; i++ {
		key[0] = u32(i)
		x += target.to_x(key)
	}

	elapsed := time.since(start)
	println('${name} : ${elapsed} ms.  result = ${x}')

}

fn performance_test() {
	for i := 1; i < 8; i++ {
		performance_test_core("basic${i}", new_long_converter(get_dimension_info(i)))
	}
}

fn main() {
	performance_test()	
}