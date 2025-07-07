package com.example.orm

/**
 * TupleFactory: 负责通过 TupleBuilder 查找和创建动态生成的元组类，并组装元组链。
 */
class TupleFactory(private val tupleBuilder: TupleBuilder) {

    /**
     * 获取或创建指定类型组合的元组分段的 Class 对象。
     * @param directTypes 当前元组分段的直接字段类型列表。
     * @param hasRestField 当前元组分段是否包含 rest 字段。
     * @return 动态生成的或 ClassLoader 中已有的元组分段的 Java Class 对象。
     */
    fun getOrCreateTupleClass(directTypes: List<Class<*>>, hasRestField: Boolean): Class<*> {
        val types = convertClassType(directTypes)
        val segmentClassName = tupleBuilder.generateTupleClassName(types, hasRestField)
        // 先查找
        return try {
            tupleBuilder.classLoader.loadClass(segmentClassName)
        } catch (_: ClassNotFoundException) {
            // 没有则生成源码并编译
            tupleBuilder.compileTupleClass(types, hasRestField, segmentClassName)
        }
    }

    /**
     * 获取或创建指定类型组合的元组链的根元组实例。
     * 此方法负责迭代构建和链接元组分段，并利用 getOrCreateTupleClass 方法的缓存。
     *
     * @param allTypes 元组中字段的 Java Class 类型列表 (完整的类型列表)。
     * @return 动态生成的元组链的根 AbstractTuple 实例。
     */
    fun getOrCreateTuple(allTypes: List<Class<*>>): AbstractTuple {
        if (allTypes.isEmpty()) {
            val emptyTupleClass = getOrCreateTupleClass(emptyList(), false)
            return emptyTupleClass.getDeclaredConstructor().newInstance() as AbstractTuple
        }
        var currentStartIndex = 0
        var rootTupleInstance: AbstractTuple? = null
        var previousTupleInstance: AbstractTuple? = null
        while (currentStartIndex < allTypes.size) {
            val currentChunkEndIndex = minOf(currentStartIndex + MAX_DIRECT_FIELDS, allTypes.size)
            val directTypesForThisSegment = allTypes.subList(currentStartIndex, currentChunkEndIndex)
            val hasRestFieldForThisSegment = currentChunkEndIndex < allTypes.size
            val segmentClass = getOrCreateTupleClass(directTypesForThisSegment, hasRestFieldForThisSegment)
            val currentTupleInstance = segmentClass.getDeclaredConstructor().newInstance() as AbstractTuple
            if (rootTupleInstance == null) {
                rootTupleInstance = currentTupleInstance
            } else {
                val restField = previousTupleInstance!!.javaClass.getDeclaredField("rest").apply { isAccessible = true }
                restField.set(previousTupleInstance, currentTupleInstance)
            }
            previousTupleInstance = currentTupleInstance
            currentStartIndex = currentChunkEndIndex
        }
        return rootTupleInstance!!
    }

    private fun convertClassType(directTypes: List<Class<*>>) : List<Class<*>>{
        if (directTypes.all { it.isPrimitive }) {
            return directTypes
        }
        // 关键修正：对于非原始类型，统一返回 Object.class
        return directTypes.map { if(it.isPrimitive) it else Any::class.java }
    }

    private companion object {
        const val MAX_DIRECT_FIELDS = 7
    }
}