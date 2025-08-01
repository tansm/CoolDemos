import net.openhft.chronicle.bytes.BytesStore
import kotlin.time.measureTime

fun main() {
    measureTime {
        for (i in 0 until 1000_0000) {
            // 创建固定容量（1024字节）的堆外内存
            val store: BytesStore<*, Void?> = BytesStore.nativeStoreWithFixedCapacity(1024)

            try {
                // 写入数据
                store.writeUtf8(0, "Hello, Chronicle!") // 在偏移0写入字符串
                store.writeInt(20, 42) // 在偏移20写入整数

                store.readInt(20)
            } finally {
                // 释放内存
                store.releaseLast()
            }
        }
    }.also { println("$it ") }
}