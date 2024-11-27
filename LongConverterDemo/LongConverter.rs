struct MemberMap {
    carry: u64,
    dimension_position: usize,
    min_value: u32,
    max_value: u32,
}

impl MemberMap {
    fn new(dimension_position: usize, members: &[u32], carry: u64) -> Self {
        let min_value = *members.iter().min().unwrap_or(&0);
        let max_value = *members.iter().max().unwrap_or(&0);

        Self {
            carry,
            dimension_position,
            min_value,
            max_value,
        }
    }

    fn step(&self) -> u64 {
        (self.max_value as u64) - (self.min_value as u64) + 1u64
    }

    #[inline(always)]
    fn to_x(&self, y: &[u32]) -> u64 {
        self.carry * ((y[self.dimension_position] - self.min_value) as u64)
    }
}

pub struct LongConverter {
    fixed_members: Vec<u32>,
    mappings: Vec<MemberMap>,
}

impl LongConverter {
    pub fn new(dimension_info: &[Vec<u32>]) -> Self {
        let mut fixed_members = vec![0; dimension_info.len()];
        let mut mappings = Vec::new();
        let mut carry = 1;

        for (i, dim) in dimension_info.iter().enumerate() {
            match dim.len() {
                0 => panic!("Empty dimension"),
                1 => fixed_members[i] = dim[0],
                _ => {
                    let map = MemberMap::new(i, dim, carry);
                    if map.step() == 0 {
                        fixed_members[i] = map.min_value;
                    } else {
                        carry *= map.step();
                        mappings.push(map);
                    }
                }
            }
        }

        Self {
            fixed_members,
            mappings,
        }
    }

    //#[inline(always)]
    pub fn to_x(&self, y: &[u32]) -> u64 {
        let mut result = 0;
        for (_, mapping) in self.mappings.iter().enumerate() {
            result += mapping.to_x(y);
        }
        result
        //self.mappings.iter().map(|mapping| mapping.to_x(y)).sum()
    }
}