package org.jetbrains.deft.proto.frontend

import kotlin.reflect.KClass

/**
 * Simple class to associate enum values by some string key.
 */
abstract class EnumMap<EnumT : Enum<EnumT>>(
    values: () -> Array<EnumT>,
    private val key: EnumT.() -> String,
    private val klass: KClass<EnumT>,
) {
    private val enumMap: Map<String, EnumT> = buildMap {
        values().forEach { put(key(it), it) }
    }
    fun fromString(value: String): EnumT? = enumMap[value]

    fun requireFromString(value: String): EnumT = enumMap[value]
        ?: error("No valid value of ${klass.simpleName} for string $value")
}

/**
 * Companion object, that adds convenient `invoke` builder function.
 */
abstract class BuilderCompanion<PartBuilderT : Any>(
    private val ctor: () -> PartBuilderT
) {
    operator fun invoke(block: PartBuilderT.() -> Unit) = ctor().apply(block)
}