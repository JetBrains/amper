/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics.helpers

import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.tree.ErrorValue
import org.jetbrains.amper.frontend.tree.ListValue
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.MapProperty
import org.jetbrains.amper.frontend.tree.NullValue
import org.jetbrains.amper.frontend.tree.RecurringTreeVisitor
import org.jetbrains.amper.frontend.tree.RecurringTreeVisitorUnit
import org.jetbrains.amper.frontend.tree.ReferenceValue
import org.jetbrains.amper.frontend.tree.ScalarProperty
import org.jetbrains.amper.frontend.tree.ScalarValue
import org.jetbrains.amper.frontend.tree.StringInterpolationValue
import org.jetbrains.amper.frontend.tree.TreeState
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.tree.declaration
import org.jetbrains.amper.frontend.tree.visitMapLikeValues
import org.jetbrains.amper.frontend.types.isSameAs
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * Visit all passed scalar properties within given [TreeValue].
 */
inline fun <reified T : SchemaNode, reified V> TreeValue<*>.visitScalarProperties(
    vararg properties: KProperty1<T, V>,
    noinline visitSelected: (ScalarProperty, V & Any) -> Unit,
) {
    ObjectPropertiesVisitorRecurring(
        objectKlass = T::class,
        properties = properties.map { it.name },
    ) {
        val pValue = it.value as? ScalarValue ?: return@ObjectPropertiesVisitorRecurring
        val value = pValue.value
        if (value is V) visitSelected(it as ScalarProperty, value)
    }.visitValue(this)
}

/**
 * Visit all passed [MapLikeValue] properties within given [TreeValue].
 */
inline fun <reified T : SchemaNode> TreeValue<*>.visitMapLikeProperties(
    vararg properties: KProperty1<T, *>,
    noinline visitSelected: (MapProperty<*>, MapLikeValue<*>) -> Unit,
) = ObjectPropertiesVisitorRecurring(
    objectKlass = T::class,
    properties = properties.map { it.name },
) {
    val pValue = it.value as? MapLikeValue<*> ?: return@ObjectPropertiesVisitorRecurring
    visitSelected(it as MapProperty<*>, pValue)
}.visitValue(this)

/**
 * Visit all passed [MapLikeValue] properties within given [TreeValue].
 */
inline fun <reified T : SchemaNode> TreeValue<*>.visitListProperties(
    vararg properties: KProperty1<T, *>,
    noinline visitSelected: (MapLikeValue.Property<ListValue<*>>, ListValue<*>) -> Unit,
) = ObjectPropertiesVisitorRecurring(
    objectKlass = T::class,
    properties = properties.map { it.name },
) {
    val pValue = it.value as? ListValue<*> ?: return@ObjectPropertiesVisitorRecurring
    visitSelected(it as MapLikeValue.Property<ListValue<*>>, pValue)
}.visitValue(this)

/**
 * Visit all objects of the matching type in the tree.
 */
inline fun <reified T : SchemaNode> TreeValue<*>.visitObjects(
    crossinline block: (MapLikeValue<*>) -> Unit
) = visitMapLikeValues { 
    if (it.declaration?.isSameAs<T>() == true) block(it)
}

/**
 * Visits properties with keys from [properties] in objects with [objectKlass] type.
 */
class ObjectPropertiesVisitorRecurring(
    private val objectKlass: KClass<out SchemaNode>,
    private val properties: Collection<String>,
    private val visitSelected: (MapLikeValue.Property<TreeValue<*>>) -> Unit,
) : RecurringTreeVisitorUnit<TreeState>() {

    override fun visitMapValue(value: MapLikeValue<*>) {
        if (value.declaration?.isSameAs(objectKlass) == true) value.children.map { it.doVisitMapProperty() }
        else super.visitMapValue(value)
    }

    fun MapLikeValue.Property<TreeValue<*>>.doVisitMapProperty() =
        if (key in properties) visitSelected(this)
        else visitValue(value)
}

/**
 * Visit all scalar properties within passed [TreeValue].
 * FIXME Need also check non scalars, but not objects.
 */
fun TreeValue<*>.collectScalarPropertiesWithOwners() = AllScalarPropertiesCollector.visitValue(this)

private typealias ScalarPropertyWithOwner = Pair<MapLikeValue<*>, ScalarProperty>
private typealias ScalarPropertiesWithOwner = List<ScalarPropertyWithOwner>

private object AllScalarPropertiesCollector : RecurringTreeVisitor<ScalarPropertiesWithOwner, TreeState>() {
    override fun visitNullValue(value: NullValue) = emptyList<ScalarPropertyWithOwner>()
    override fun visitScalarValue(value: ScalarValue) = emptyList<ScalarPropertyWithOwner>()
    override fun visitNoValue(value: ErrorValue) = emptyList<ScalarPropertyWithOwner>()
    override fun visitReferenceValue(value: ReferenceValue) = emptyList<ScalarPropertyWithOwner>()
    override fun visitStringInterpolationValue(value: StringInterpolationValue) = emptyList<ScalarPropertyWithOwner>()
    override fun aggregate(value: TreeValue<*>, childResults: List<ScalarPropertiesWithOwner>) = childResults.flatten()
    override fun visitMapValue(value: MapLikeValue<*>) = super.visitMapValue(value) +
            value.children.filter { it.value is ScalarValue }.map { value to (it as ScalarProperty) }
}