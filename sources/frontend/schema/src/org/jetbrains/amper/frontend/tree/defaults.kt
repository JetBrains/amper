/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.api.Default
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.contexts.DefaultContext
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.stdlib.collections.IdentityHashSet

context(buildCtx: BuildCtx)
internal fun MapLikeValue<*>.appendDefaultValues(): Merged {
    /*
      "root for defaults" is a MapLike<*> value with a type (object value), that is:
        a) the tree root
        b) a value of a property that had a `null` default or has no default
           (map entries also fall into this category)
        c) a value that has a list parent.
       -----------------------------------
       Examples:
       ```kotlin
       class Foo {
         val bar: Bar? get() = null
         val foo-prop get() = "foo-default"
       }
       class Bar {
         val baz: Baz get() = Baz()
         val bar-prop get() = "bar-default"
       }
       class Baz {
         val baz-prop get() = "baz-default"
         val foos: List<Foo> get() = emptyList()
       }
       ```
       -----------------------------------
       Now, imagine we have a tree:
       ```yaml
       foo:                                  # root - true root (a)
         bar:                                # root - null by default, but the value present (b)
           baz:
             baz-prop: "baz-overridden"
             foos:
               - foo-prop: "foo-overridden"  # root - list parent (c)
       ```
       We want to append the defaults like this:
       ```yaml
       foo:
         foo-prop (default): "foo-default"
         bar:
           bar-prop (default): "bar-default"
           baz (default):
             baz-prop (default): "baz-default"
             foos (default): []
           baz:
             baz-prop: "baz-overridden"
             foos:
               - foo-prop (default): "foo-default"
                 foo-prop: "foo-overridden"
       ```
       For this we only append defaults to the "roots for defaults" values.
       Appending defaults to every object value would bloat the tree (make the tree almost complete).
       That would still work, but we want to avoid complete trees.
     */
    val rootsForDefaults = IdentityHashSet<MapLikeValue<*>>()
    rootsForDefaults += this  // (a)

    fun TreeValue<*>.findRootsForDefaults(): Unit = when (this) {
        is ListValue<*> -> children.forEach { value ->
            if (value is MapLikeValue<*> && value.type is SchemaType.ObjectType) {
                rootsForDefaults += value  // (c)
            }
            value.findRootsForDefaults()
        }
        is MapLikeValue<*> -> for (child in children) {
            child.value.findRootsForDefaults()
            val value = child.value as? MapLikeValue<*>
            if (value?.type !is SchemaType.ObjectType) {
                continue
            }
            val default = declaration?.getProperty(child.key)?.default
            if (default == null || (default is Default.Static && default.value == null)) {
                rootsForDefaults += value  // (b)
            }
        }
        else -> Unit
    }

    findRootsForDefaults()

    val appender = DefaultsAppender(
        rootsForDefaults = rootsForDefaults,
    )
    val withDefaults = appender.transform(this)!! as MapLikeValue<*>
    return buildCtx.treeMerger.mergeTrees(listOf(withDefaults))
}


/**
 * Visitor that is adding default values with special [DefaultTrace] trace and [TypeLevelDefaultContexts] context to the tree.
 */
private class DefaultsAppender(
    val rootsForDefaults: Set<MapLikeValue<*>>,
) : TreeTransformer<TreeState>() {
    override fun visitMapValue(value: MapLikeValue<TreeState>): TransformResult<MapLikeValue<TreeState>> {
        val transformResult = super.visitMapValue(value)

        val declaration = value.declaration
        if (declaration == null || value !in rootsForDefaults)
            return transformResult

        val transformedValue = when (transformResult) {
            is Changed -> transformResult.value
            NotChanged -> value
            Removed -> error("Unexpected remove")
        }

        val defaultProperties = createDefaultProperties(declaration)
        if (defaultProperties.isEmpty()) {
            return transformResult
        }

        return Changed(transformedValue.copy(children = transformedValue.children + defaultProperties))
    }
}

private fun createDefaultProperties(declaration: SchemaObjectDeclaration): MapLikeChildren<TreeState> =
    declaration.properties.mapNotNull { property ->
        val value = property.default?.toTreeValue(
            type = property.type,
        ) ?: return@mapNotNull null
        MapLikeValue.Property(property.name, DefaultTrace, value, property)
    }

private fun Default<*>.toTreeValue(type: SchemaType): TreeValue<TreeState>? = when (this) {
    is Default.Static -> toTreeValue(type)
    is Default.NestedObject -> {
        check(type is SchemaType.ObjectType)
        Owned(createDefaultProperties(type.declaration), type, DefaultTrace, TypeLevelDefaultContexts)
    }
    is Default.DirectDependent -> ReferenceValue(listOf(property.name), type, DefaultTrace, TypeLevelDefaultContexts)
    is Default.TransformedDependent<*, *> -> {
        // FIXME: Not yet supported! Need to rethink this default kind and implement it in another way
        null
    }
}

private fun Default.Static<*>.toTreeValue(type: SchemaType): TreeValue<TreeState> {
    val value = value
    return if (value == null) {
        check(type.isMarkedNullable)
        NullValue(DefaultTrace, TypeLevelDefaultContexts)
    } else when (type) {
        is SchemaType.ScalarType -> ScalarValue(value, type, DefaultTrace, TypeLevelDefaultContexts)
        is SchemaType.ListType -> {
            check(value is List<*>)
            check(value.isEmpty() || type.elementType is SchemaType.ScalarType) {
                "Non-empty lists as defaults are allowed only for lists with scalar element types"
            }
            val children = value.map { Default.Static(it).toTreeValue(type.elementType) }
            ListValue(children, type, DefaultTrace, TypeLevelDefaultContexts)
        }
        is SchemaType.MapType -> {
            check(value == emptyMap<Nothing, Nothing>()) {
                "Only an empty map is permitted as a default for a map property. " +
                        "If there are cases, you'll need to extend the implementation here"
            }
            Owned(emptyList(), type, DefaultTrace, TypeLevelDefaultContexts)
        }
        is SchemaType.ObjectType, is SchemaType.VariantType -> {
            error("Static defaults for object types are not supported")
        }
    }
}

private val TypeLevelDefaultContexts = listOf(DefaultContext.TypeLevel)