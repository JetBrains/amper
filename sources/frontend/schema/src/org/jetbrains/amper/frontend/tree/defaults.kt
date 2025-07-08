/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.api.Default
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.contexts.DefaultCtxs
import org.jetbrains.amper.frontend.types.SchemaType

context(BuildCtx)
internal fun TreeValue<Merged>.appendDefaultValues(): MapLikeValue<Merged> =
    DefaultsAppender.visitValue(this)!!
        .let { treeMerger.mergeTrees(listOf(it as MapLikeValue<*>)) }

/**
 * Visitor that is adding default leaves with special [DefaultTrace] trace and default values to the tree.
 */
private object DefaultsAppender : TreeTransformer<TreeState>() {
    override fun visitMapValue(value: MapLikeValue<TreeState>): TreeValue<TreeState>? {
        val aObject = value.type ?: return super.visitMapValue(value)

        // Note: We are using special [DefaultCtxs] that has the least possible priority during merge.
        val toAddDefaults: MapLikeChildren<TreeState> =
            aObject.properties.mapNotNull out@{
                when (val default = it.default) {
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
                        it.name,
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
                        (it.type as? SchemaType.ObjectType)?.declaration ?: return@out null,
                    )

                    // Other cases are unsupported.
                    else -> return@out null
                }
            }

        return if (toAddDefaults.isEmpty()) super.visitMapValue(value)
        else {
            val extendedToAddDefaults = toAddDefaults.visitAll()
            val childrenWithDefaults = with(DefaultsRootsDiscoverer) { value.children.visitAll() }
            // We can't guarantee any tree contract here, so we have to create an Owned value.
            Owned(
                children = (childrenWithDefaults + extendedToAddDefaults) as MapLikeChildren<Owned>,
                type = value.type,
                trace = value.trace,
                contexts = value.contexts,
            )
        }
    }
}

private object DefaultsRootsDiscoverer : TreeTransformer<TreeState>() {
    override fun visitListValue(value: ListValue<TreeState>): TreeValue<TreeState>? = DefaultsAppender.visitValue(value)
}

/**
 * Construct a default value tree from the passed value.
 */
private fun defaultValueFrom(value: Any, type: SchemaType): OwnedTree = when {
    type is SchemaType.MapType && value is Map<*, *> -> Owned(
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

    type is SchemaType.ListType && value is List<*> -> ListValue(
        children = value.mapNotNull { defaultValueFrom(it ?: return@mapNotNull null, type.elementType) },
        trace = DefaultTrace,
        contexts = DefaultCtxs,
    )

    type is SchemaType.ScalarType -> ScalarValue(
        value = value,
        trace = DefaultTrace,
        contexts = DefaultCtxs,
    )

    // TODO Report.
    else -> error("Not matching type: $type")
}