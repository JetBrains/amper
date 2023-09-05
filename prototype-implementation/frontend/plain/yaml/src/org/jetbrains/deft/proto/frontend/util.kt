package org.jetbrains.deft.proto.frontend


/**
 * Companion object, that adds convenient `invoke` builder function.
 */
abstract class BuilderCompanion<PartBuilderT : Any>(
    private val ctor: () -> PartBuilderT
) {
    operator fun invoke(block: PartBuilderT.() -> Unit) = ctor().apply(block)
}