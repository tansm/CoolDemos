#[cfg(test)]
mod tests {
    use std::time::Instant;
    use RustDemos::LongConverter::LongConverter;

    fn get_dimension_info(size: usize) -> Vec<Vec<u32>> {
        match size {
            1 => vec![
                vec![900],
                vec![0, 1, 9, 5],
                vec![900],
                vec![900],
                vec![900],
                vec![900],
                vec![900],
                vec![900],
                vec![900],
                vec![900],
                vec![900],
            ],
            2 => vec![
                vec![900],
                vec![0, 1, 9, 5],
                vec![900],
                vec![900, 832],
                vec![900],
                vec![900],
                vec![900],
                vec![900],
                vec![900],
                vec![900],
                vec![900],
            ],
            3 => vec![
                vec![900],
                vec![0, 1, 9, 5],
                vec![900],
                vec![900, 832],
                vec![3, 6, 8, 4, 2, 2, 44, 56, 67, 32, 123],
                vec![900],
                vec![900],
                vec![900],
                vec![900],
                vec![900],
                vec![900],
            ],
            4 => vec![
                vec![900],
                vec![0, 1, 9, 5],
                vec![900],
                vec![900, 832],
                vec![3, 6, 8, 4, 2, 2, 44, 56, 67, 32, 123],
                vec![900],
                vec![900],
                vec![1000_0000, 1000_0002, 1000_0001],
                vec![900],
                vec![900],
                vec![900],
            ],
            5 => vec![
                vec![900],
                vec![0, 1, 9, 5],
                vec![900],
                vec![900, 832],
                vec![3, 6, 8, 4, 2, 2, 44, 56, 67, 32, 123],
                vec![900],
                vec![900],
                vec![1000_0000, 1000_0002, 1000_0001],
                vec![900],
                vec![4, 9, 10],
                vec![900],
            ],
            6 => vec![
                vec![900],
                vec![0, 1, 9, 5],
                vec![900],
                vec![900, 832],
                vec![3, 6, 8, 4, 2, 2, 44, 56, 67, 32, 123],
                vec![900],
                vec![900],
                vec![1000_0000, 1000_0002, 1000_0001],
                vec![900],
                vec![4, 9, 10],
                vec![9, 4],
            ],
            7 => vec![
                vec![8, 7],
                vec![0, 1, 9, 5],
                vec![900],
                vec![900, 832],
                vec![3, 6, 8, 4, 2, 2, 44, 56, 67, 32, 123],
                vec![900],
                vec![900],
                vec![10_000_000, 10_000_002, 10_000_001],
                vec![900],
                vec![4, 9, 10],
                vec![9, 4],
            ],
            _ => panic!("Invalid size"),
        }
    }

    fn performance_test_core(name: &str, target: &LongConverter) {
        let mut key:[u32; 11] = [9, 5, 900, 832, 67, 800, 100_0000, 1000_0001, 3, 4, 9];
        let mut x = 0u64;
        let start = Instant::now();
        for i in 0..i32::MAX {
            key[0] = i as u32;
            x += target.to_x(&key);
        }
        let duration = start.elapsed();
        println!("{}: {} ms. result = {}", name, duration.as_millis(), x);
    }

    #[test]
    fn performance_test() {
        for i in 1..8 {
            let dimension_info = get_dimension_info(i);
            let converter = LongConverter::new(&dimension_info);
            performance_test_core(&format!("basic{}", i), &converter);
        }
    }
}