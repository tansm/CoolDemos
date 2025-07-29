# BigDecimalArray

## 中文部分

### 项目目标：解决的问题
在企业级 Java 应用（JDK 1.8）中，实体类（如 `SaleOrder`）常包含大量 `BigDecimal` 字段，用于存储高精度十进制数据（如金额）。每个 `BigDecimal` 对象在 64 位 JVM（启用压缩指针）中占用约 40 字节，60 个字段导致内存占用高达 2652 字节（含字段引用和对象头）。这在高并发、大数据量场景下会造成显著内存开销。

**问题**：
- `BigDecimal` 对象头和字段（如 `intCompact`, `scale`, `intVal`）导致内存占用较大。
- 大量字段（如 60 个）重复定义 `get`/`set` 方法，代码复杂且维护困难。
- 原始设计中 `BigDecimal` 字段可为 `null`，新设计需保留此语义。

**目标**：
- 减少内存占用，优化存储效率。
- 简化代码生成，降低维护成本。
- 保留 `BigDecimal` 的接口兼容性和 `null` 值语义。

### BigDecimal 的存储机制
根据 JDK 1.8 的 `BigDecimal` 源码（[BigDecimal.java](https://github.com/frohoff/jdk8u-dev-jdk/blob/master/src/share/classes/java/math/BigDecimal.java)），`BigDecimal` 的核心字段包括：
- `long intCompact` (8 字节)：存储未缩放值（unscaled value），当值在 `long` 范围内（`-2^63` 到 `2^63-1`）时使用。
- `int scale` (4 字节)：小数点位置，定义实际值为 `intCompact × 10^(-scale)`。
- `BigInteger intVal` (4 字节引用，`null` 或指向大数值)：当值超出 `long` 范围时使用。
- `int precision` 和 `String stringCache`：辅助字段，通常未缓存（`null` 或 0）。

**内存占用**：
- 单个 `BigDecimal`：对象头 (12 字节) + 字段 (28 字节) = 40 字节（对齐）。
- 作为字段：每个字段需额外 4 字节引用（压缩指针）。
- 60 个字段：60 × (40 + 4) = 2640 字节 + 实体对象头 (12 字节) = 2652 字节。

### 我们的解决方案
`BigDecimalArray` 是一个非静态类，提供高效的 `BigDecimal` 数组存储方案，通过以下方式解决内存和代码复杂性问题：
1. **数组存储**：
   - 使用 `long[] intCompacts`、`byte[] scales` 和 `BigDecimal[] objects` 存储 60 个字段的未缩放值、小数点位置和缓存对象。
   - 小数值（`long` 范围内，`scale` 在 -128 到 127）：存储在 `intCompacts` 和 `scales`，`objects` 未分配或 `objects[index] = null`，每个字段 12 字节（假设 `objects` 未分配）。
   - 大数值（超出 `long`）或大 `scale`（超出 `byte`）：存储在 `objects[index]`，`intCompacts[index] = INFLATED`（`Long.MIN_VALUE`），`scales[index] = 0`。
2. **默认初始化为 `null`**：
   - `intCompacts` 初始化所有元素为 `INFLATED`，确保默认状态等价于 `null`。
3. **延迟初始化 `BigDecimal[]`**：
   - `objects` 默认未分配（`null`），仅在存储大数值或大 `scale` 时分配，节省 256 字节。
4. **简化 `null` 处理**：
   - `get` 检查 `intCompacts[index] == INFLATED`，返回 `objects[index]`（`null` 或大数值）。
5. **Unsafe 优化**：
   - 使用 `sun.misc.Unsafe` 访问 `BigDecimal` 的 `intCompact` 字段，判断是否压缩存储。
   - 使用 `value.scale()` 获取 `scale`，避免反射开销。
6. **数组接口**：
   - 提供 `getSize()` 方法，返回数组大小，增强接口完整性。

**内存优化**：
- 默认（`objects` 未分配）：480 (`long[]`) + 80 (`byte[]`) + 4 (`objects` 引用) + 4 (`size`) + 32 (数组对象头) + 28 (`BigDecimalArray` 对象头+字段) + 20 (`SaleOrder` 对象头+字段) = 648 字节，节省约 75%（2652 字节 → 648 字节）。
- 分配 `objects`：648 + 256 (`BigDecimal[]`：240 字节引用 + 16 字节数组头）= 900 字节，节省约 66%。
- 大数值场景：部分字段存储 `BigDecimal`（40 字节/个），仍优于原始方案。

### 使用方法
1. **添加依赖**：
   - 将项目克隆到本地，或添加为 Maven/Gradle 依赖（待发布）。
   - 确保 JDK 1.8 环境，`sun.misc.Unsafe` 可访问。

2. **定义实体类**：
   ```java
   package org.example;

   import java.math.BigDecimal;

   public class SaleOrder {
       private static final int FIELD_COUNT = 60;
       private final BigDecimalArray decimalArray = new BigDecimalArray(FIELD_COUNT);

       // 仅用于测试，生产中不建议公开
       public BigDecimal getItem(int index) {
           return decimalArray.get(index);
       }

       public void setItem(int index, BigDecimal value) {
           decimalArray.set(index, value);
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

       // ... 直到 getItem59, setItem59
   }
   ```

3. **使用示例**：
   ```java
   SaleOrder order = new SaleOrder();
   // 设置值
   order.setItem0(null); // null 值
   order.setItem1(new BigDecimal("123.45")); // 小数值
   order.setItem2(new BigDecimal("12345678901234567890.123")); // 大数值
   order.setItem3(BigDecimal.ZERO); // 零值
   order.setItem4(BigDecimal.valueOf(12345, -2)); // 负 scale
   order.setItem5(BigDecimal.valueOf(123, 128)); // 大 scale

   // 获取值
   System.out.println(order.getItem0()); // null
   System.out.println(order.getItem1()); // 123.45
   System.out.println(order.getItem2()); // 12345678901234567890.123
   System.out.println(order.getItem3()); // 0
   System.out.println(order.getItem4()); // 1234500
   System.out.println(order.getItem5()); // 0.000...123 (128 位小数)
   ```

4. **注意事项**：
   - 确保索引在 0 到 `FIELD_COUNT-1` 之间，否则抛出 `IllegalArgumentException`。
   - `Long.MIN_VALUE`（`INFLATED`）用于表示 `null`，若业务中此值常见，需改用 `boolean[] isNulls`（增加约 80 字节）。
   - `Unsafe` 依赖可能受 JVM 限制，需测试兼容性。
   - `scale` 超出 `byte` 范围（-128 到 127）时存储为 `BigDecimal`，确保功能完整。
   - `getItem` 和 `setItem` 仅用于测试，生产中应使用 `getItemX` 和 `setItemX` 方法。

---

## English Section

### Project Goal: Problem Addressed
In enterprise Java applications (JDK 1.8), entity classes (e.g., `SaleOrder`) often contain numerous `BigDecimal` fields for high-precision decimal data (e.g., financial amounts). Each `BigDecimal` object consumes approximately 40 bytes in a 64-bit JVM with compressed pointers, leading to significant memory overhead for entities with 60 `BigDecimal` fields (total ~2652 bytes, including field references and object headers). This is problematic in high-concurrency or large-scale scenarios.

**Problems**:
- High memory usage due to `BigDecimal` object headers and fields (`intCompact`, `scale`, `intVal`).
- Code complexity from defining `get`/`set` methods for numerous fields (e.g., 60 fields).
- Preserving `null` value semantics from the original design.

**Goals**:
- Reduce memory usage and optimize storage efficiency.
- Simplify code generation and maintenance.
- Maintain `BigDecimal` interface compatibility and `null` value semantics.

### How BigDecimal Stores Data
Based on the JDK 1.8 `BigDecimal` source ([BigDecimal.java](https://github.com/frohoff/jdk8u-dev-jdk/blob/master/src/share/classes/java/math/BigDecimal.java)), `BigDecimal` uses the following fields:
- `long intCompact` (8 bytes): Stores the unscaled value when within `long` range (`-2^63` to `2^63-1`).
- `int scale` (4 bytes): Defines the decimal point position, where the actual value is `intCompact × 10^(-scale)`.
- `BigInteger intVal` (4 bytes reference, `null` or large value): Used when the value exceeds `long` range.
- `int precision` and `String stringCache`: Auxiliary fields, typically `null` or 0 when not cached.

**Memory Usage**:
- Single `BigDecimal`: 12 bytes (object header) + 28 bytes (fields) = 40 bytes (aligned).
- As fields: Each field requires an additional 4-byte reference (compressed pointers).
- 60 fields: 60 × (40 + 4) = 2640 bytes + entity header (12 bytes) = 2652 bytes.

### Our Solution
`BigDecimalArray` is a non-static class providing an efficient storage solution for `BigDecimal` arrays, addressing memory and code complexity issues through:
1. **Array-Based Storage**:
   - Uses `long[] intCompacts`, `byte[] scales`, and `BigDecimal[] objects` to store 60 fields' unscaled values, scales, and cached objects.
   - Small values (within `long`, `scale` in -128 to 127): Stored in `intCompacts` and `scales`, with `objects` unallocated or `objects[index] = null`, 12 bytes per field (if `objects` unallocated).
   - Large values (beyond `long`) or large `scale` (beyond `byte`): Stored in `objects[index]`, with `intCompacts[index] = INFLATED` (`Long.MIN_VALUE`), `scales[index] = 0`.
2. **Default Null Initialization**:
   - `intCompacts` is initialized with `INFLATED`, ensuring fields default to `null`.
3. **Lazy Initialization of `BigDecimal[]`**:
   - `objects` defaults to `null`, allocated only when storing large values or large `scale`, saving 256 bytes.
4. **Simplified Null Handling**:
   - `get` checks `intCompacts[index] == INFLATED`, returning `objects[index]` (`null` or large value).
5. **Unsafe Optimization**:
   - Uses `sun.misc.Unsafe` to access `BigDecimal`’s `intCompact` field, determining if compression is possible.
   - Uses `value.scale()` to avoid reflection for scale access.
6. **Array Interface**:
   - Provides `getSize()` method, returning the array size, enhancing interface completeness.

**Memory Savings**:
- Default (no `objects`): 480 (`long[]`) + 80 (`byte[]`) + 4 (`objects` reference) + 4 (`size`) + 32 (array headers) + 28 (`BigDecimalArray` header+fields) + 20 (`SaleOrder` header+fields) = 648 bytes, saving ~75% (2652 bytes → 648 bytes).
- With `objects` allocated: 648 + 256 (`BigDecimal[]`: 240 bytes references + 16 bytes array header) = 900 bytes, saving ~66%.
- Large value scenario: Some fields store `BigDecimal` (40 bytes each), still outperforms original design.

### How to Use This Library
1. **Add Dependency**:
   - Clone the repository or add as a Maven/Gradle dependency (to be published).
   - Ensure JDK 1.8 and `sun.misc.Unsafe` accessibility.

2. **Define Entity Class**:
   ```java
   package org.example;

   import java.math.BigDecimal;

   public class SaleOrder {
       private static final int FIELD_COUNT = 60;
       private final BigDecimalArray decimalArray = new BigDecimalArray(FIELD_COUNT);

       // For testing only; not recommended for production
       public BigDecimal getItem(int index) {
           return decimalArray.get(index);
       }

       public void setItem(int index, BigDecimal value) {
           decimalArray.set(index, value);
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

       // ... up to getItem59, setItem59
   }
   ```

3. **Usage Example**:
   ```java
   SaleOrder order = new SaleOrder();
   // Set values
   order.setItem0(null); // Null value
   order.setItem1(new BigDecimal("123.45")); // Small number
   order.setItem2(new BigDecimal("12345678901234567890.123")); // Large number
   order.setItem3(BigDecimal.ZERO); // Zero value
   order.setItem4(BigDecimal.valueOf(12345, -2)); // Negative scale
   order.setItem5(BigDecimal.valueOf(123, 128)); // Large scale

   // Get values
   System.out.println(order.getItem0()); // null
   System.out.println(order.getItem1()); // 123.45
   System.out.println(order.getItem2()); // 12345678901234567890.123
   System.out.println(order.getItem3()); // 0
   System.out.println(order.getItem4()); // 1234500
   System.out.println(order.getItem5()); // 0.000...123 (128 decimal places)
   ```

4. **Notes**:
   - Ensure indices are between 0 and `FIELD_COUNT-1`, or an `IllegalArgumentException` will be thrown.
   - `Long.MIN_VALUE` (`INFLATED`) is used to indicate `null`. If this is a common value in your data, consider using a `boolean[] isNulls` array (adds ~80 bytes).
   - `Unsafe` dependency may be restricted in some JVMs; test compatibility.
   - `scale` values outside `byte` range (-128 to 127) are stored as `BigDecimal`, ensuring full functionality.
   - `getItem` and `setItem` are for testing only; use `getItemX` and `setItemX` methods in production.