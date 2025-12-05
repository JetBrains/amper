/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics.helpers

import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.tree.ErrorValue
import org.jetbrains.amper.frontend.tree.ListValue
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.NullValue
import org.jetbrains.amper.frontend.tree.RecurringTreeVisitor
import org.jetbrains.amper.frontend.tree.RecurringTreeVisitorUnit
import org.jetbrains.amper.frontend.tree.ReferenceValue
import org.jetbrains.amper.frontend.tree.ScalarValue
import org.jetbrains.amper.frontend.tree.StringInterpolationValue
import org.jetbrains.amper.frontend.tree.TreeState
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.tree.declaration
import org.jetbrains.amper.frontend.types.isSameAs
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * Visit all passed scalar properties within given [TreeValue].
 */
inline fun <reified T : SchemaNode, reified V> TreeValue<*>.visitScalarProperties(
    vararg properties: KProperty1<T, V>,
    noinline visitSelected: (MapLikeValue.Property<*>, V & Any) -> Unit,
) {
    ObjectPropertiesVisitorRecurring(
        objectKlass = T::class,
        properties = properties.map { it.name },
    ) {
        val pValue = it.value as? ScalarValue ?: return@ObjectPropertiesVisitorRecurring
        val value = pValue.value
        if (value is V) visitSelected(it, value)
    }.visitValue(this)
}

/**
 * Visit all passed [MapLikeValue] properties within given [TreeValue].
 */
inline fun <reified T : SchemaNode> TreeValue<*>.visitListProperties(
    vararg properties: KProperty1<T, *>,
    noinline visitSelected: (MapLikeValue.Property<*>, ListValue<*>) -> Unit,
) = ObjectPropertiesVisitorRecurring(
    objectKlass = T::class,
    properties = properties.map { it.name },
) {
    val pValue = it.value as? ListValue<*> ?: return@ObjectPropertiesVisitorRecurring
    visitSelected(it, pValue)
}.visitValue(this)

/**
 * Visits properties with keys from [properties] in objects with [objectKlass] type.
 */
class ObjectPropertiesVisitorRecurring(
    private val objectKlass: KClass<out SchemaNode>,
    private val properties: Collection<String>,
    private val visitSelected: (MapLikeValue.Property<TreeValue<*>>) -> Unit,
) : RecurringTreeVisitorUnit<TreeState>() {

    override fun visitMapValue(value: MapLikeValue<*>) {
        if (value.declaration?.isSameAs(objectKlass) == true) value.children.forEach { it.doVisitMapProperty() }
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

private typealias PropertyWithOwner = Pair<MapLikeValue<*>, MapLikeValue.Property<*>>
private typealias PropertiesWithOwner = List<PropertyWithOwner>

private object AllScalarPropertiesCollector : RecurringTreeVisitor<PropertiesWithOwner, TreeState>() {
    override fun visitNullValue(value: NullValue) = emptyList<Nothing>()
    override fun visitScalarValue(value: ScalarValue) = emptyList<Nothing>()
    override fun visitNoValue(value: ErrorValue) = emptyList<Nothing>()
    override fun visitReferenceValue(value: ReferenceValue) = emptyList<Nothing>()
    override fun visitStringInterpolationValue(value: StringInterpolationValue) = emptyList<Nothing>()
    override fun aggregate(value: TreeValue<*>, childResults: List<PropertiesWithOwner>) = childResults.flatten()
    override fun visitMapValue(value: MapLikeValue<*>) = super.visitMapValue(value) +
            value.children.filter { it.value is ScalarValue }.map { value to it }
}