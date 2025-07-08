/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics.helpers

import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.tree.RecurringTreeVisitor
import org.jetbrains.amper.frontend.tree.RecurringTreeVisitorUnit
import org.jetbrains.amper.frontend.tree.ListValue
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.MapProperty
import org.jetbrains.amper.frontend.tree.Merged
import org.jetbrains.amper.frontend.tree.MergedTree
import org.jetbrains.amper.frontend.tree.NoValue
import org.jetbrains.amper.frontend.tree.ReferenceValue
import org.jetbrains.amper.frontend.tree.ScalarProperty
import org.jetbrains.amper.frontend.tree.ScalarValue
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.tree.visitMapLikeValues
import org.jetbrains.amper.frontend.types.isSameAs
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1


/**
 * Visit all passed scalar properties within given [TreeValue].
 */
inline fun <reified T : SchemaNode, reified V> MergedTree.visitScalarProperties(
    vararg properties: KProperty1<T, V>,
    noinline visitSelected: (ScalarProperty<Merged>, V & Any) -> Unit,
) {
    ObjectPropertiesVisitorRecurring(
        objectKlass = T::class,
        properties = properties.map { it.name },
    ) {
        val pValue = it.value as? ScalarValue<*> ?: return@ObjectPropertiesVisitorRecurring
        val value = pValue.value
        if (value is V) visitSelected(it as ScalarProperty<Merged>, value)
    }.visitValue(this)
}

/**
 * Visit all passed [MapLikeValue] properties within given [TreeValue].
 */
inline fun <reified T : SchemaNode> MergedTree.visitMapLikeProperties(
    vararg properties: KProperty1<T, *>,
    noinline visitSelected: (MapProperty<Merged>, MapLikeValue<Merged>) -> Unit,
) = ObjectPropertiesVisitorRecurring(
    objectKlass = T::class,
    properties = properties.map { it.name },
) {
    val pValue = it.value as? MapLikeValue<Merged> ?: return@ObjectPropertiesVisitorRecurring
    visitSelected(it as MapProperty<Merged>, pValue)
}.visitValue(this)

/**
 * Visit all passed [MapLikeValue] properties within given [TreeValue].
 */
inline fun <reified T : SchemaNode> MergedTree.visitListProperties(
    vararg properties: KProperty1<T, *>,
    noinline visitSelected: (MapLikeValue.Property<ListValue<Merged>>, ListValue<Merged>) -> Unit,
) = ObjectPropertiesVisitorRecurring(
    objectKlass = T::class,
    properties = properties.map { it.name },
) {
    val pValue = it.value as? ListValue<Merged> ?: return@ObjectPropertiesVisitorRecurring
    visitSelected(it as MapLikeValue.Property<ListValue<Merged>>, pValue)
}.visitValue(this)

/**
 * Visit all objects of the matching type in the tree.
 */
inline fun <reified T : SchemaNode> MergedTree.visitObjects(
    crossinline block: (MapLikeValue<Merged>) -> Unit
) = visitMapLikeValues { 
    if (it.type?.isSameAs<T>() == true) block(it)
}

/**
 * Visits properties with keys from [properties] in objects with [objectKlass] type.
 */
class ObjectPropertiesVisitorRecurring(
    private val objectKlass: KClass<out SchemaNode>,
    private val properties: Collection<String>,
    private val visitSelected: (MapLikeValue.Property<MergedTree>) -> Unit,
) : RecurringTreeVisitorUnit<Merged>() {

    override fun visitMapValue(value: MapLikeValue<Merged>) {
        if (value.type?.isSameAs(objectKlass) == true) value.children.map { it.doVisitMapProperty() }
        else super.visitMapValue(value)
    }

    fun MapLikeValue.Property<MergedTree>.doVisitMapProperty() =
        if (key in properties) visitSelected(this)
        else visitValue(value)
}

/**
 * Visit all scalar properties within passed [TreeValue].
 * FIXME Need also check non scalars, but not objects.
 */
fun MergedTree.collectScalarPropertiesWithOwners() = AllScalarPropertiesCollector.visitValue(this)

private typealias ScalarPropertyWithOwner = Pair<MapLikeValue<Merged>, ScalarProperty<Merged>>
private typealias ScalarPropertiesWithOwner = List<ScalarPropertyWithOwner>

private object AllScalarPropertiesCollector : RecurringTreeVisitor<ScalarPropertiesWithOwner, Merged>() {
    override fun visitScalarValue(value: ScalarValue<Merged>) = emptyList<ScalarPropertyWithOwner>()
    override fun visitNoValue(value: NoValue<*>) = emptyList<ScalarPropertyWithOwner>()
    override fun visitReferenceValue(value: ReferenceValue<Merged>) = emptyList<ScalarPropertyWithOwner>()
    override fun aggregate(value: MergedTree, childResults: List<ScalarPropertiesWithOwner>) = childResults.flatten()
    override fun visitMapValue(value: MapLikeValue<Merged>) = super.visitMapValue(value) +
            value.children.filter { it.value is ScalarValue }.map { value to (it as ScalarProperty<Merged>) }
}