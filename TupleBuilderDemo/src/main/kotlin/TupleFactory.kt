package com.example.orm

import com.example.orm.TupleUtils.MAX_DIRECT_FIELDS
import java.io.StringWriter
import java.net.URI
import javax.tools.DiagnosticCollector
import javax.tools.FileObject
import javax.tools.ForwardingJavaFileManager
import javax.tools.JavaCompiler
import javax.tools.JavaFileManager
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.StandardJavaFileManager
import javax.tools.ToolProvider

/**
 * TupleFactory: 负责通过 ClassLoader 查找和创建动态生成的元组类。
 * 现在负责构建和缓存整个元组链。
 */
class TupleFactory(private val tupleBuilder: TupleBuilder) {

    // Custom ClassLoader to load compiled classes from memory
    private class InMemoryClassLoader(parent: ClassLoader) : ClassLoader(parent) {
        private val classBytes = mutableMapOf<String, ByteArray>()

        fun addClass(className: String, bytes: ByteArray) {
            classBytes[className] = bytes
        }

        override fun findClass(name: String): Class<*>? {
            val bytes = classBytes[name]
            return if (bytes != null) {
                defineClass(name, bytes, 0, bytes.size)
            } else {
                super.findClass(name)
            }
        }
    }

    private val compiler: JavaCompiler = ToolProvider.getSystemJavaCompiler()
        ?: throw IllegalStateException("JDK (not JRE) is required for runtime compilation.")
    private val fileManager: StandardJavaFileManager = compiler.getStandardFileManager(null, null, null)
    private val inMemoryClassLoader = InMemoryClassLoader(Thread.currentThread().contextClassLoader)

    // Custom JavaFileObject to hold source code in memory
    private class StringJavaFileObject(val className: String, val code: String) : SimpleJavaFileObject(
        URI.create("string:///" + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension), JavaFileObject.Kind.SOURCE
    ) {
        override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = code
    }

    // Custom JavaFileObject to store compiled bytecode in memory
    private class ByteArrayJavaFileObject(name: String, kind: JavaFileObject.Kind) : SimpleJavaFileObject(
        URI.create("string:///" + name.replace('.', '/') + kind.extension), kind
    ) {
        var bytes: ByteArray? = null
        override fun openInputStream() = bytes?.inputStream() ?: throw IllegalStateException("Bytes not set")
        override fun openOutputStream() = object : java.io.ByteArrayOutputStream() {
            override fun close() {
                super.close()
                bytes = toByteArray()
            }
        }
    }

    // Custom JavaFileManager to handle in-memory source and bytecode
    private class InMemoryJavaFileManager(standardManager: StandardJavaFileManager, val classLoader: InMemoryClassLoader) :
        ForwardingJavaFileManager<JavaFileManager>(standardManager) {

        private val outputFiles = mutableMapOf<String, ByteArrayJavaFileObject>()

        override fun getJavaFileForOutput(
            location: JavaFileManager.Location?,
            className: String,
            kind: JavaFileObject.Kind,
            sibling: FileObject?
        ): JavaFileObject {
            val outputFile = ByteArrayJavaFileObject(className, kind)
            outputFiles[className] = outputFile
            return outputFile
        }

        override fun getClassLoader(location: JavaFileManager.Location?): ClassLoader {
            return classLoader
        }

        fun getBytes(className: String): ByteArray? {
            return outputFiles[className]?.bytes
        }
    }

    private val inMemoryFileManager = InMemoryJavaFileManager(fileManager, inMemoryClassLoader)

    private fun convertClassType(directTypes: List<Class<*>>) : List<Class<*>>{
        if (directTypes.all { it.isPrimitive }) {
            return directTypes
        }

        // 关键修正：对于非原始类型，统一返回 Object.class
        return directTypes.map { if(it.isPrimitive) it else Any::class.java }
    }

    /**
     * 获取或创建指定类型组合的元组分段的 Class 对象。
     * 此方法利用 ClassLoader 的缓存机制。
     *
     * @param directTypes 当前元组分段的直接字段类型列表。
     * @param hasRestField 当前元组分段是否包含 rest 字段。
     * @return 动态生成的或 ClassLoader 中已有的元组分段的 Java Class 对象。
     */
    fun getOrCreateTupleClass(directTypes: List<Class<*>>, hasRestField: Boolean): Class<*> {
        val types = convertClassType(directTypes)
        // 获取当前分段的类名
        val segmentClassName = tupleBuilder.generateTupleClassName(types, hasRestField)

        // 尝试从 ClassLoader 缓存中获取该分段的 Class
        return try {
            inMemoryClassLoader.loadClass(segmentClassName)
        } catch (_: ClassNotFoundException) {
            // 如果缓存中没有，则通过 TupleBuilder 构建源代码并编译
            val sourceCode = tupleBuilder.buildTupleSource(types, hasRestField)
            val compilationUnits = listOf(StringJavaFileObject(segmentClassName, sourceCode))
            val diagnostics = DiagnosticCollector<JavaFileObject>()

            val task = compiler.getTask(
                StringWriter(), // Output for compiler messages (not used here, but required)
                inMemoryFileManager,
                diagnostics,
                null, // options
                null, // classes
                compilationUnits
            )

            val success = task.call()
            if (!success) {
                diagnostics.diagnostics.forEach { println(it) }
                throw RuntimeException("Compilation failed for $segmentClassName. Diagnostics: $diagnostics")
            }

            // 加载编译后的字节码到自定义 ClassLoader
            val compiledBytes = inMemoryFileManager.getBytes(segmentClassName)
            if (compiledBytes == null) {
                throw IllegalStateException("Compiled bytes not found for $segmentClassName after successful compilation.")
            }
            inMemoryClassLoader.addClass(segmentClassName, compiledBytes)

            // 从自定义 ClassLoader 中加载并返回 Class
            inMemoryClassLoader.loadClass(segmentClassName)
        }
    }

    /**
     * 获取或创建指定类型组合的元组链的根元组实例。
     * 此方法负责迭代构建和链接元组分段，并利用 getOrCreateTupleClass 方法的缓存。
     *
     * @param allTypes 元组中字段的 Java Class 类型列表 (完整的类型列表)。
     * @return 动态生成的元组链的根 ITuple 实例。
     */
    fun getOrCreateTuple(allTypes: List<Class<*>>): ITuple {
        if (allTypes.isEmpty()) {
            // 对于空元组，直接创建并返回一个空的 ITuple 实例
            val emptyTupleClass = getOrCreateTupleClass(emptyList(), false)
            return emptyTupleClass.newInstance() as ITuple
        }

        var currentStartIndex = 0
        var rootTupleInstance: ITuple? = null
        var previousTupleInstance: ITuple? = null

        // 迭代构建元组链
        while (currentStartIndex < allTypes.size) {
            val currentChunkEndIndex = minOf(currentStartIndex + MAX_DIRECT_FIELDS, allTypes.size)
            val directTypesForThisSegment = allTypes.subList(currentStartIndex, currentChunkEndIndex)
            val hasRestFieldForThisSegment = currentChunkEndIndex < allTypes.size

            // 获取当前分段的 Class，利用 getOrCreateTupleClass 的缓存
            val segmentClass = getOrCreateTupleClass(directTypesForThisSegment, hasRestFieldForThisSegment)

            // 实例化当前分段的元组
            val currentTupleInstance = segmentClass.newInstance() as ITuple

            // 链接元组链
            if (rootTupleInstance == null) {
                rootTupleInstance = currentTupleInstance // 第一个实例是根
            } else {
                // 获取前一个元组实例的 rest 字段并设置当前实例
                val restField = previousTupleInstance!!.javaClass.getDeclaredField("rest").apply { isAccessible = true }
                restField.set(previousTupleInstance, currentTupleInstance)
            }

            previousTupleInstance = currentTupleInstance // 更新 previousTupleInstance
            currentStartIndex = currentChunkEndIndex // 移动到下一个分段的起始索引
        }
        return rootTupleInstance!!
    }
}