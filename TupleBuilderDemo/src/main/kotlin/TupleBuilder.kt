package com.example.orm

import java.io.StringWriter
import java.net.URI
import javax.tools.*

/**
 * TupleBuilder: 负责根据给定的类型组合动态创建元组类的 Java 源代码字符串，并负责编译和加载。
 */
class TupleBuilder(
    val basePackage: String = "com.kingdee.orm.generated",
    parentClassLoader: ClassLoader = Thread.currentThread().contextClassLoader
) {
    // 内部内存 ClassLoader
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
    private val inMemoryClassLoader = InMemoryClassLoader(parentClassLoader)

    // 内存中的 Java 源文件对象
    private class StringJavaFileObject(className: String, val code: String) : SimpleJavaFileObject(
        URI.create("string:///" + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension), JavaFileObject.Kind.SOURCE
    ) {
        override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = code
    }

    // 内存中的字节码对象
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

    // 内存文件管理器
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

    val classLoader : ClassLoader get() = inMemoryClassLoader

    fun compileTupleClass(types: List<Class<*>>, hasRestField: Boolean, segmentClassName: String): Class<*> {
        val sourceCode = buildTupleSource(types, hasRestField)
        val compilationUnits = listOf(StringJavaFileObject(segmentClassName, sourceCode))
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val task = compiler.getTask(
            StringWriter(),
            inMemoryFileManager,
            diagnostics,
            null,
            null,
            compilationUnits
        )
        val success = task.call()
        if (!success) {
            diagnostics.diagnostics.forEach { println(it) }
            throw RuntimeException("Compilation failed for $segmentClassName. Diagnostics: $diagnostics")
        }
        val compiledBytes = inMemoryFileManager.getBytes(segmentClassName)
        if (compiledBytes == null) {
            throw IllegalStateException("Compiled bytes not found for $segmentClassName after successful compilation.")
        }
        inMemoryClassLoader.addClass(segmentClassName, compiledBytes)
        return inMemoryClassLoader.loadClass(segmentClassName)
    }

    // 下面为源码生成相关内容（与原有一致）
    private fun getTypeAbbreviation(clazz: Class<*>): String {
        return when (clazz) {
            Integer.TYPE -> "I"
            java.lang.Long.TYPE -> "L"
            java.lang.Boolean.TYPE -> "B"
            java.lang.Byte.TYPE -> "Y"
            java.lang.Short.TYPE -> "S"
            Character.TYPE -> "C"
            java.lang.Float.TYPE -> "F"
            java.lang.Double.TYPE -> "D"
            else -> "O"
        }
    }

    fun generateTupleClassName(directTypes: List<Class<*>>, hasRestField: Boolean): String {
        val typeAbbr = directTypes.joinToString("") { getTypeAbbreviation(it) }
        val restSuffix = if (hasRestField) "R" else ""
        return "$basePackage.Tuple_${typeAbbr}$restSuffix"
    }

    fun buildTupleSource(directTypes: List<Class<*>>, hasRestField: Boolean): String {
        val className = generateTupleClassName(directTypes, hasRestField).substringAfterLast('.')
        val generatedSource = with(StringBuilder()) {
            append("package $basePackage;\n\n")
            val tfName = AbstractTuple::class.java.name
            append("import $tfName;\n")
            append("import java.lang.Class;\n")
            append("public final class $className extends ${AbstractTuple::class.java.simpleName} {\n")
            directTypes.forEachIndexed { index, clazz ->
                val fieldType = if (clazz.isPrimitive) clazz.canonicalName else "Object"
                append("    public $fieldType item$index;\n")
            }
            if (hasRestField) {
                append("    public ${AbstractTuple::class.java.canonicalName} rest;\n")
            }
            append("\n")
            append("    public $className() {}\n\n")
            append("    @Override\n")
            append("    public int getDirectSize() {\n")
            append("        return ${directTypes.size};\n")
            append("    }\n\n")
            if (hasRestField) {
                append("@Override\n")
                append("public AbstractTuple getRest() {\n")
                append("    return rest;\n")
                append("}\n\n")
                append("@Override\n")
                append("public boolean getHasRestField() {\n")
                append("    return true;\n")
                append("}\n\n")
            }
            append("    @Override\n")
            append("    public Class<?> getFieldType(int index) {\n")
            append("        if (index < 0) {\n")
            append("            return throwIndexOutOfBounds(index);\n")
            append("        }\n")
            append("        switch (index) {\n")
            directTypes.forEachIndexed { i, clazz ->
                val returnType = if (clazz.isPrimitive) clazz.canonicalName else "java.lang.Object"
                append("            case $i: return $returnType.class;\n")
            }
            if (hasRestField) {
                append("            default:\n")
                append("                if (this.rest == null) {\n")
                append("                    throwIllegalStateException(index);\n")
                append("                }\n")
                append("                return this.rest.getFieldType(index - ${directTypes.size});\n")
            } else {
                append("            default: return throwIndexOutOfBounds(index);\n")
            }
            append("        }\n")
            append("    }\n\n")
            append("    @Override\n")
            append("    public Object getItem(int index) {\n")
            append("        if (index < 0) {\n")
            append("            return throwIndexOutOfBounds(index);\n")
            append("        }\n")
            append("        switch (index) {\n")
            directTypes.forEachIndexed { i, _ ->
                append("            case $i: return this.item$i;\n")
            }
            if (hasRestField) {
                append("            default:\n")
                append("                if (this.rest == null) {\n")
                append("                    return throwIndexOutOfBounds(index);\n")
                append("                }\n")
                append("                return this.rest.getItem(index - ${directTypes.size});\n")
            } else {
                append("            default: return throwIndexOutOfBounds(index);\n")
            }
            append("        }\n")
            append("    }\n\n")
            append("    @Override\n")
            append("    public void setItem(int index, Object value) {\n")
            append("        if (index < 0) {\n")
            append("            throwIndexOutOfBounds(index);\n")
            append("            return;\n")
            append("        }\n")
            append("        switch (index) {\n")
            directTypes.forEachIndexed { i, clazz ->
                if (clazz.isPrimitive) {
                    val cast = when (clazz) {
                        Integer.TYPE -> "((Integer)value).intValue()"
                        java.lang.Long.TYPE -> "((Long)value).longValue()"
                        java.lang.Boolean.TYPE -> "((Boolean)value).booleanValue()"
                        java.lang.Byte.TYPE -> "((Byte)value).byteValue()"
                        java.lang.Short.TYPE -> "((Short)value).shortValue()"
                        Character.TYPE -> "((Character)value).charValue()"
                        java.lang.Float.TYPE -> "((Float)value).floatValue()"
                        java.lang.Double.TYPE -> "((Double)value).doubleValue()"
                        else -> "value"
                    }
                    append("            case $i: this.item$i = $cast; return;\n")
                } else {
                    append("            case $i: this.item$i = value; return;\n")
                }
            }
            if (hasRestField) {
                append("            default:\n")
                append("                if (this.rest == null) {\n")
                append("                    throwIllegalStateException(index);\n")
                append("                }\n")
                append("                this.rest.setItem(index - ${directTypes.size}, value);\n")
            } else {
                append("            default: throwIndexOutOfBounds(index);\n")
            }
            append("        }\n")
            append("    }\n\n")
            append(generatePrimitiveGetterSource("Int", "int", "java.lang.Integer", directTypes, hasRestField))
            append(generatePrimitiveGetterSource("Long", "long", "java.lang.Long", directTypes, hasRestField))
            append(generatePrimitiveGetterSource("Boolean", "boolean", "java.lang.Boolean", directTypes, hasRestField))
            append(generatePrimitiveGetterSource("Byte", "byte", "java.lang.Byte", directTypes, hasRestField))
            append(generatePrimitiveGetterSource("Short", "short", "java.lang.Short", directTypes, hasRestField))
            append(generatePrimitiveGetterSource("Char", "char", "java.lang.Character", directTypes, hasRestField))
            append(generatePrimitiveGetterSource("Float", "float", "java.lang.Float", directTypes, hasRestField))
            append(generatePrimitiveGetterSource("Double", "double", "java.lang.Double", directTypes, hasRestField))
            append(generatePrimitiveSetterSource("Int", "int", "java.lang.Integer", directTypes, hasRestField))
            append(generatePrimitiveSetterSource("Long", "long", "java.lang.Long", directTypes, hasRestField))
            append(generatePrimitiveSetterSource("Boolean", "boolean", "java.lang.Boolean", directTypes, hasRestField))
            append(generatePrimitiveSetterSource("Byte", "byte", "java.lang.Byte", directTypes, hasRestField))
            append(generatePrimitiveSetterSource("Short", "short", "java.lang.Short", directTypes, hasRestField))
            append(generatePrimitiveSetterSource("Char", "char", "java.lang.Character", directTypes, hasRestField))
            append(generatePrimitiveSetterSource("Float", "float", "java.lang.Float", directTypes, hasRestField))
            append(generatePrimitiveSetterSource("Double", "double", "java.lang.Double", directTypes, hasRestField))
            append("}\n")
            toString()
        }
        //println("Generated Source for $className:\n$generatedSource")
        return generatedSource
    }

    private fun generatePrimitiveGetterSource(
        methodNameSuffix: String,
        primitiveTypeName: String,
        wrapperTypeName: String,
        directTypes: List<Class<*>>,
        hasRestField: Boolean
    ): String {
        val correctTypeIndices = mutableListOf<Int>()
        val wrongTypeIndices = mutableListOf<Int>()
        directTypes.forEachIndexed { i, clazz ->
            if (clazz.name == primitiveTypeName || clazz.canonicalName == wrapperTypeName) {
                correctTypeIndices.add(i)
            } else {
                wrongTypeIndices.add(i)
            }
        }
        if (correctTypeIndices.isEmpty()) {
            return ""
        }
        with(StringBuilder()) {
            append("    @Override\n")
            append("    public $primitiveTypeName get$methodNameSuffix(int index) {\n")
            append("        if (index < 0) {\n")
            append("            return throwIndexOutOfBounds(index);\n")
            append("        }\n")
            append("        switch (index) {\n")
            correctTypeIndices.forEach { i ->
                append("            case $i: return this.item$i;\n")
            }
            // JDK 1.8 兼容写法：每个 case 单独一行
            wrongTypeIndices.forEach { i ->
                append("            case $i:\n")
            }
            if (wrongTypeIndices.isNotEmpty()) {
                append("                throwClassCastException(index, \"$methodNameSuffix\");\n")
            }
            if (hasRestField) {
                append("            default:\n")
                append("                if (this.rest == null) {\n")
                append("                    throwIllegalStateException(index);\n")
                append("                }\n")
                append("                return this.rest.get$methodNameSuffix(index - ${directTypes.size});\n")
            } else {
                append("            default: return throwIndexOutOfBounds(index);\n")
            }
            append("        }\n")
            append("    }\n\n")
            return toString()
        }
    }

    private fun generatePrimitiveSetterSource(
        methodNameSuffix: String,
        primitiveTypeName: String,
        wrapperTypeName: String,
        directTypes: List<Class<*>>,
        hasRestField: Boolean
    ): String {
        val correctTypeIndices = mutableListOf<Int>()
        val wrongTypeIndices = mutableListOf<Int>()
        directTypes.forEachIndexed { i, clazz ->
            if (clazz.name == primitiveTypeName || clazz.canonicalName == wrapperTypeName) {
                correctTypeIndices.add(i)
            } else {
                wrongTypeIndices.add(i)
            }
        }
        if (correctTypeIndices.isEmpty()) {
            return ""
        }
        with(StringBuilder()) {
            append("    @Override\n")
            append("    public void set$methodNameSuffix(int index, $primitiveTypeName value) {\n")
            append("        if (index < 0) {\n")
            append("            throwIndexOutOfBounds(index);\n")
            append("            return;\n")
            append("        }\n")
            append("        switch (index) {\n")
            correctTypeIndices.forEach { i ->
                append("            case $i: this.item$i = value; return;\n")
            }
            // JDK 1.8 兼容写法：每个 case 单独一行
            wrongTypeIndices.forEach { i ->
                append("            case $i:\n")
            }
            if (wrongTypeIndices.isNotEmpty()) {
                append("                throwClassCastException(index, \"$methodNameSuffix\");\n")
            }
            if (hasRestField) {
                append("            default:\n")
                append("                if (this.rest == null) {\n")
                append("                    throwIllegalStateException(index);\n")
                append("                }\n")
                append("                this.rest.set$methodNameSuffix(index - ${directTypes.size}, value);\n")
            } else {
                append("            default: throwIndexOutOfBounds(index);\n")
            }
            append("        }\n")
            append("    }\n\n")
            return toString()
        }
    }
}
