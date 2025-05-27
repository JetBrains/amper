/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import com.intellij.util.asSafely
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.api.Default
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.contexts.DefaultCtx
import org.jetbrains.amper.frontend.contexts.DefaultCtxs
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.types.ATypes


context(BuildCtx)
fun TreeValue<Merged>.appendDefaultValues() = DefaultsAppender(types).visitValue(this)!!

/**
 * Visitor that is adding default leaves with special [DefaultTrace] trace and default values to the tree.
 *
 * // FIXME That is not 100% true, need to rethink/rewrite.
 * Note: Since this visitor is duplicating intermediate properties so it does not change [Merged] state of the tree.
 */
class DefaultsAppender(
    val types: ATypes,
) : TreeTransformer<Merged>() {
    override fun visitMapValue(value: MapLikeValue<Merged>): MergedTree? {
        val aObject = value.type?.asSafely<ATypes.AObject>() ?: return super.visitMapValue(value)
        val emptyContextLeavesKeys = value.children.filter { it.value.contexts.isEmpty() }.map { it.key }.toSet()

        // Note: We are using special [DefaultCtxs] that has the least possible priority during merge.
        val toAddDefaults: MapLikeChildren<Merged> =
            aObject.properties.filter { it.meta.name !in emptyContextLeavesKeys }.mapNotNull out@{
                val default = it.meta.default
                when (default) {
                    // Default as a reference creates a reference value to a referenced property.
                    is Default.Dependent<*, *> -> ReferenceProperty(
                        it,
                        DefaultTrace,
                        default.property.takeIf { default.isReference }?.name ?: return@out null,
                        DefaultTrace,
                        DefaultCtxs,
                    )

                    // Default as a static value creates a scalar value.
                    is Default.Static<*> -> MapLikeValue.Property(
                        it.meta.name,
                        DefaultTrace,
                        defaultValueFrom(default.value ?: return@out null, it.type),
                        it,
                    )

                    // In case if default is pointing to SchemaObject then we create an empty [MapProperty]
                    // and it will be filled within [acceptAll] call.
                    is Default.Lambda<*> -> MapProperty(
                        it,
                        DefaultTrace,
                        DefaultTrace,
                        DefaultCtxs,
                        it.type as? ATypes.AObject ?: return@out null,
                    )

                    // Other cases are unsupported.
                    else -> return@out null
                }
            }

        return if (toAddDefaults.isEmpty()) super.visitMapValue(value)
        else value.copy(children = value.children.plus(toAddDefaults).visitAll())
    }

    /**
     * Construct a default value tree from the passed value.
     */
    private fun defaultValueFrom(value: Any, type: ATypes.AType): MergedTree = when {
        type is ATypes.AMap && value is Map<*, *> -> MapLikeValue(
            children = value
                .mapNotNull {
                    MapLikeValue.Property(
                        it.key.toString(),
                        DefaultTrace,
                        defaultValueFrom(it.value ?: return@mapNotNull null, type.valueType),
                        null,
                    )
                },
            trace = DefaultTrace,
            contexts = DefaultCtxs,
            type = null,
        )

        type is ATypes.AList && value is List<*> -> ListValue(
            children = value.mapNotNull { defaultValueFrom(it ?: return@mapNotNull null, type.valueType) },
            trace = DefaultTrace,
            contexts = DefaultCtxs,
        )

        type is ATypes.AScalar -> ScalarValue(
            value = value,
            trace = DefaultTrace,
            contexts = DefaultCtxs,
        )

        // TODO Report.
        else -> error("Not matching type: $type")
    }
}