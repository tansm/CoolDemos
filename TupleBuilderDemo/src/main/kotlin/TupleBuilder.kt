package com.example.orm

/**
 * TupleBuilder: 负责根据给定的类型组合动态创建元组类的 Java 源代码字符串。
 * 它不再使用 Byte Buddy，而是生成可编译的 Java 源代码。
 */
class TupleBuilder(
    val basePackage: String = "com.kingdee.orm.generated"
) {

    /**
     * 辅助函数：根据 Class 获取其单字母缩写。
     * 原始类型使用首字母，所有引用类型（包括 String）统一为 'O' (Object)。
     */
    private fun getTypeAbbreviation(clazz: Class<*>): String {
        return when (clazz) {
            Integer.TYPE -> "I" // int.class
            java.lang.Long.TYPE -> "L"    // long.class
            java.lang.Boolean.TYPE -> "B" // boolean.class
            java.lang.Byte.TYPE -> "Y"    // byte.class
            java.lang.Short.TYPE -> "S"   // short.class
            Character.TYPE -> "C" // char.class
            java.lang.Float.TYPE -> "F"   // float.class
            java.lang.Double.TYPE -> "D"  // double.class
            else -> "O" // 所有其他引用类型，包括 String, Any, 以及自定义对象，都用 'O' 表示 Object
        }
    }

    /**
     * 公共函数：计算动态生成的元组类的全限定名。
     * 此方法只基于当前分段的类型缩写和是否包含 rest 字段生成名称。
     *
     * @param directTypes 当前元组分段的直接字段类型列表。
     * @param hasRestField 当前元组分段是否包含 rest 字段。
     * @return 计算出的类全限定名。
     */
    fun generateTupleClassName(directTypes: List<Class<*>>, hasRestField: Boolean): String {
        val typeAbbr = directTypes.joinToString("") { getTypeAbbreviation(it) }
        val restSuffix = if (hasRestField) getTypeAbbreviation(ITuple::class.java) else "" // 'O' for ITuple
        // 格式：Tuple_TypeAbbr[RestSuffix]
        return "$basePackage.Tuple_${typeAbbr}$restSuffix"
    }

    /**
     * 构建一个元组类的 Java 源代码字符串。
     *
     * @param directTypes 当前元组块的字段类型列表 (最多 MAX_DIRECT_FIELDS 个)。
     * @param hasRestField 当前元组块是否包含 rest 字段。
     * @return 动态生成的元组分段的 Java 源代码字符串。
     */
    fun buildTupleSource(directTypes: List<Class<*>>, hasRestField: Boolean): String {
        val className = generateTupleClassName(directTypes, hasRestField).substringAfterLast('.')
        val fullClassName = generateTupleClassName(directTypes, hasRestField)

        val sourceBuilder = StringBuilder()
        sourceBuilder.append("package $basePackage;\n\n")
        val itfName = ITuple::class.java.name // 获取 ITuple 的全限定名
        sourceBuilder.append("import $itfName;\n") // 使用全限定名导入
        sourceBuilder.append("import java.lang.Class;\n")
        // 导入统一的异常帮助类
        sourceBuilder.append("import ${TupleUtils::class.java.name};\n\n")

        sourceBuilder.append("public final class $className implements ${ITuple::class.java.simpleName} {\n")

        // 定义字段
        directTypes.forEachIndexed { index, clazz ->
            // 字段类型声明也应该与 getFieldType 的返回类型一致：非原始类型声明为 Object
            val fieldType = if (clazz.isPrimitive) clazz.canonicalName else "java.lang.Object"
            sourceBuilder.append("    public $fieldType item$index;\n")
        }
        if (hasRestField) {
            sourceBuilder.append("    public ${ITuple::class.java.canonicalName} rest;\n")
        }
        sourceBuilder.append("\n")

        // 默认构造函数
        sourceBuilder.append("    public $className() {}\n\n")

        // getSize() 方法
        sourceBuilder.append("    @Override\n")
        sourceBuilder.append("    public int getSize() {\n")
        sourceBuilder.append("        int size = ${directTypes.size};\n")
        if (hasRestField) {
            sourceBuilder.append("        if (this.rest != null) {\n")
            sourceBuilder.append("            size += this.rest.getSize();\n")
            sourceBuilder.append("        }\n")
        }
        sourceBuilder.append("        return size;\n")
        sourceBuilder.append("    }\n\n")

        // getFieldType(index) 方法
        sourceBuilder.append("    @Override\n")
        sourceBuilder.append("    public Class<?> getFieldType(int index) {\n")
        sourceBuilder.append("        if (index < 0) {\n")
        sourceBuilder.append("            return ${TupleUtils::class.java.simpleName}.throwIndexOutOfBounds(index);\n") // 调用统一方法
        sourceBuilder.append("        }\n")
        sourceBuilder.append("        switch (index) {\n")
        directTypes.forEachIndexed { i, clazz ->
            // 关键修正：对于非原始类型，统一返回 Object.class
            val returnType = if (clazz.isPrimitive) clazz.canonicalName else "java.lang.Object"
            sourceBuilder.append("            case $i: return $returnType.class;\n")
        }
        if (hasRestField) {
            sourceBuilder.append("            default:\n")
            sourceBuilder.append("                if (this.rest == null) {\n")
            sourceBuilder.append("                    ${TupleUtils::class.java.simpleName}.throwIllegalStateException(index);\n") // 调用统一方法
            sourceBuilder.append("                }\n")
            sourceBuilder.append("                return this.rest.getFieldType(index - ${directTypes.size});\n")
        } else {
            sourceBuilder.append("            default: return ${TupleUtils::class.java.simpleName}.throwIndexOutOfBounds(index);\n") // 调用统一方法
        }
        sourceBuilder.append("        }\n")
        sourceBuilder.append("    }\n\n")

        // getItem(index) 方法 (需要处理装箱)
        sourceBuilder.append("    @Override\n")
        sourceBuilder.append("    public Object getItem(int index) {\n")
        sourceBuilder.append("        if (index < 0) {\n")
        sourceBuilder.append("            return ${TupleUtils::class.java.simpleName}.throwIndexOutOfBounds(index);\n") // 调用统一方法
        sourceBuilder.append("        }\n")
        sourceBuilder.append("        switch (index) {\n")
        directTypes.forEachIndexed { i, _ ->
            sourceBuilder.append("            case $i: return this.item$i;\n")
        }
        if (hasRestField) {
            sourceBuilder.append("            default:\n")
            sourceBuilder.append("                if (this.rest == null) {\n")
            sourceBuilder.append("                    return ${TupleUtils::class.java.simpleName}.throwIndexOutOfBounds(index);\n") // 调用统一方法
            sourceBuilder.append("                }\n")
            sourceBuilder.append("                return this.rest.getItem(index - ${directTypes.size});\n")
        } else {
            sourceBuilder.append("            default: return ${TupleUtils::class.java.simpleName}.throwIndexOutOfBounds(index);\n") // 调用统一方法
        }
        sourceBuilder.append("        }\n")
        sourceBuilder.append("    }\n\n")

        // 生成所有原始类型 getter 方法
        sourceBuilder.append(generatePrimitiveGetterSource("Int", "int", "java.lang.Integer", directTypes, hasRestField))
        sourceBuilder.append(generatePrimitiveGetterSource("Long", "long", "java.lang.Long", directTypes, hasRestField))
        sourceBuilder.append(generatePrimitiveGetterSource("Boolean", "boolean", "java.lang.Boolean", directTypes, hasRestField))
        sourceBuilder.append(generatePrimitiveGetterSource("Byte", "byte", "java.lang.Byte", directTypes, hasRestField))
        sourceBuilder.append(generatePrimitiveGetterSource("Short", "short", "java.lang.Short", directTypes, hasRestField))
        sourceBuilder.append(generatePrimitiveGetterSource("Char", "char", "java.lang.Character", directTypes, hasRestField))
        sourceBuilder.append(generatePrimitiveGetterSource("Float", "float", "java.lang.Float", directTypes, hasRestField))
        sourceBuilder.append(generatePrimitiveGetterSource("Double", "double", "java.lang.Double", directTypes, hasRestField))

        // toString() 方法
        sourceBuilder.append("    @Override\n")
        sourceBuilder.append("    public String toString() {\n")
        sourceBuilder.append("        StringBuilder sb = new StringBuilder(\"$className(\");\n")
        directTypes.forEachIndexed { index, _ ->
            sourceBuilder.append("        sb.append(\"item$index=\").append(this.item$index);\n")
            if (index < directTypes.size - 1) {
                sourceBuilder.append("        sb.append(\", \");\n")
            }
        }
        if (hasRestField) {
            if (directTypes.isNotEmpty()) {
                sourceBuilder.append("        sb.append(\", \");\n")
            }
            sourceBuilder.append("        sb.append(\"rest=\").append(this.rest);\n")
        }
        sourceBuilder.append("        sb.append(\")\");\n")
        sourceBuilder.append("        return sb.toString();\n")
        sourceBuilder.append("    }\n")

        sourceBuilder.append("}\n")

        val generatedSource = sourceBuilder.toString()
        println("Generated Source for $fullClassName:\n$generatedSource") // 打印生成的源代码

        return generatedSource
    }

    /**
     * 生成原始类型 getter 方法的源代码。
     *
     * @param methodNameSuffix 方法名后缀 (例如 "Int", "Long")。
     * @param primitiveTypeName 原始类型名称 (例如 "int", "long")。
     * @param wrapperTypeName 包装类型名称 (例如 "java.lang.Integer", "java.lang.Long")。
     * @param directTypes 当前元组块的直接字段类型列表。
     * @param hasRestField 当前元组块是否包含 rest 字段。
     * @return 生成的 getter 方法的源代码字符串。
     */
    private fun generatePrimitiveGetterSource(
        methodNameSuffix: String,
        primitiveTypeName: String,
        wrapperTypeName: String,
        directTypes: List<Class<*>>,
        hasRestField: Boolean
    ): String {
        val methodBuilder = StringBuilder()
        methodBuilder.append("    @Override\n")
        methodBuilder.append("    public $primitiveTypeName get$methodNameSuffix(int index) {\n")
        methodBuilder.append("        if (index < 0) {\n")
        methodBuilder.append("            return ${TupleUtils::class.java.simpleName}.throwIndexOutOfBounds(index);\n") // 调用统一方法
        methodBuilder.append("        }\n")
        methodBuilder.append("        switch (index) {\n")

        val correctTypeIndices = mutableListOf<Int>()
        val wrongTypeIndices = mutableListOf<Int>()

        directTypes.forEachIndexed { i, clazz ->
            if (clazz.name == primitiveTypeName || clazz.canonicalName == wrapperTypeName) {
                correctTypeIndices.add(i)
            } else {
                wrongTypeIndices.add(i)
            }
        }

        // Generate cases for correct types (individual for clarity and correctness if mixed)
        correctTypeIndices.forEach { i ->
            methodBuilder.append("            case $i: return this.item$i;\n")
        }

        // Generate cases for wrong types, grouped if consecutive
        if (wrongTypeIndices.isNotEmpty()) {
            val groupedIndices = mutableListOf<MutableList<Int>>()
            var currentGroup = mutableListOf<Int>()

            wrongTypeIndices.forEach { idx ->
                if (currentGroup.isEmpty() || currentGroup.last() == idx - 1) {
                    currentGroup.add(idx)
                } else {
                    groupedIndices.add(currentGroup)
                    currentGroup = mutableListOf(idx)
                }
            }
            if (currentGroup.isNotEmpty()) {
                groupedIndices.add(currentGroup)
            }

            groupedIndices.forEach { group ->
                methodBuilder.append("            case ${group.joinToString(",")}: ${TupleUtils::class.java.simpleName}.throwClassCastException(index, \"$methodNameSuffix\");\n")
            }
        }

        if (hasRestField) {
            methodBuilder.append("            default:\n")
            methodBuilder.append("                if (this.rest == null) {\n")
            methodBuilder.append("                    ${TupleUtils::class.java.simpleName}.throwIllegalStateException(index);\n") // 调用统一方法
            methodBuilder.append("                }\n")
            methodBuilder.append("                return this.rest.get$methodNameSuffix(index - ${directTypes.size});\n")
        } else {
            methodBuilder.append("            default: return ${TupleUtils::class.java.simpleName}.throwIndexOutOfBounds(index);\n") // 调用统一方法
        }
        methodBuilder.append("        }\n")
        methodBuilder.append("    }\n\n")
        return methodBuilder.toString()
    }
}
