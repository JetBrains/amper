/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.valueTracking

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.Default
import org.jetbrains.amper.frontend.api.PlatformSpecific
import org.jetbrains.amper.frontend.api.ProductTypeSpecific
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.SchemaValueDelegate
import org.jetbrains.amper.frontend.api.SchemaValuesVisitor
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.api.schemaDelegate
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.schema.Settings
import kotlin.collections.Set
import kotlin.reflect.full.findAnnotation

private sealed class PropertyWithSource(
    val name: String,
    val applicablePlatforms: Set<Platform>,
    val applicableProductTypes: Set<ProductType>,
) {
    class PropertyWithPrimitiveValue(
        name: String,
        val source: ValueSource?,
        val value: Any?,
        applicablePlatforms: Set<Platform>,
        applicableProductTypes: Set<ProductType>,
    ) : PropertyWithSource(name, applicablePlatforms, applicableProductTypes)

    class PropertyWithObjectValue(
        name: String,
        val valueObjectProperties: List<PropertyWithSource>,
        applicablePlatforms: Set<Platform>,
        applicableProductTypes: Set<ProductType>,
    ) : PropertyWithSource(name, applicablePlatforms, applicableProductTypes)
}

private sealed class ValueSource {
    data object Default : ValueSource()
    class DependentDefault(val desc: String, val element: PsiElement?) : ValueSource()
    class Element(val element: PsiElement) : ValueSource()
}

private class CollectingVisitor(
    private val properties: MutableList<PropertyWithSource>,
) : SchemaValuesVisitor() {

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun visitSchemaValueDelegate(valueBase: SchemaValueDelegate<*>) {
        val applicablePlatforms = valueBase.property.findAnnotation<PlatformSpecific>()?.platforms?.toSet()
            ?: Platform.entries.toSet()
        val applicableProductTypes = valueBase.property.findAnnotation<ProductTypeSpecific>()?.productTypes?.toSet()
            ?: ProductType.entries.toSet()
        val value = when {
            valueBase.value is SchemaNode -> {
                val innerProperties = mutableListOf<PropertyWithSource>()
                CollectingVisitor(innerProperties).visit(valueBase.value)
                PropertyWithSource.PropertyWithObjectValue(
                    name = valueBase.property.name,
                    valueObjectProperties = innerProperties,
                    applicablePlatforms = applicablePlatforms,
                    applicableProductTypes = applicableProductTypes
                )
            }

            valueBase.value == null && valueBase.default != null -> {
                val innerProperties = mutableListOf<PropertyWithSource>()
                CollectingVisitor(innerProperties).visit(valueBase.default!!.value)
                PropertyWithSource.PropertyWithObjectValue(
                    name = valueBase.property.name,
                    valueObjectProperties = innerProperties,
                    applicablePlatforms = applicablePlatforms,
                    applicableProductTypes = applicableProductTypes
                )
            }

            valueBase.value != null -> valueBase.value.let { value ->
                PropertyWithSource.PropertyWithPrimitiveValue(
                    name = valueBase.property.name,
                    source = (valueBase.trace as? PsiTrace)?.let { ValueSource.Element(it.psiElement) }
                        ?: valueBase.default?.takeIf { valueBase.value == valueBase.default!!.value }
                            ?.let { def ->
                                if (def is Default.Dependent<*, *>) {
                                    ValueSource.DependentDefault(
                                        def.desc,
                                        def.property.schemaDelegate.extractPsiElementOrNull()
                                    )
                                } else {
                                    ValueSource.Default
                                }
                            },
                    value = value,
                    applicablePlatforms = applicablePlatforms,
                    applicableProductTypes = applicableProductTypes
                )
            }

            else -> null
        }
        if (value != null) properties.add(value)
    }
}

fun renderSettings(
    value: Settings,
    product: ProductType,
    contexts: Set<Platform>
): String = renderObject(value, product, contexts)

private fun renderObject(
    value: SchemaNode,
    product: ProductType,
    contexts: Set<Platform>
): String = renderProperties(value.findProperties(), product, contexts)

private fun Any.findProperties(): List<PropertyWithSource> {
    val obj = this@findProperties
    return buildList {
        CollectingVisitor(this).visit(obj)
    }
}

private fun renderProperties(properties: List<PropertyWithSource>, product: ProductType, contexts: Set<Platform>): String = buildString {
    properties
        .filter {
            product in it.applicableProductTypes && contexts.intersect(it.applicablePlatforms).isNotEmpty()
        }
        .forEach { prop ->
            append(
                "${prop.name}: ${
                    when (prop) {
                        is PropertyWithSource.PropertyWithObjectValue -> "\n" + renderProperties(prop.valueObjectProperties, product, contexts)
                            .prependIndent("   ")
                        is PropertyWithSource.PropertyWithPrimitiveValue -> {
                            val value = prop.value
                            presentableValue(value, product, contexts) + (sourcePostfix(prop).takeIf { value !is Collection<*> || value.isEmpty() } ?: "")
                        }
                    }
                }\n")
        }
}

private fun formatSourceName(sourceName: String): String = "  # [$sourceName]"

private fun sourcePostfix(it: PropertyWithSource.PropertyWithPrimitiveValue
): String {
    val sourceName = when (it.source) {
        ValueSource.Default -> "default"
        is ValueSource.DependentDefault -> it.source.desc + (it.source.element?.let { element ->
            element.containingFilename()?.let {
                if (it.isNotBlank()) " @ $it" else ""
            }
        }.orEmpty())

        is ValueSource.Element -> it.source.element.containingFilename()
        null -> null
    }
    return if (!sourceName.isNullOrBlank()) formatSourceName(sourceName) else ""
}

private fun presentableValue(it: Any?, product: ProductType, contexts: Set<Platform>): String {
    return when {
        it is TraceableEnum<*> && it.value is SchemaEnum -> (it.value as SchemaEnum).schemaValue
        it is SchemaEnum -> it.schemaValue
        it is Collection<*> && it.isEmpty() -> "[]"
        it is Collection<*> && it.all { it is Traceable } -> renderTraceableCollection(it, product, contexts)
        it is Collection<*> -> "[" + it.joinToString { presentableValue(it, product, contexts) } + "]"
        it is SchemaNode -> renderObject(it, product, contexts)
        else -> it.toString()
    }
}

private fun renderTraceableCollection(it: Collection<*>, product: ProductType, contexts: Set<Platform>): String = "[\n" +
        it.mapIndexed { index, element ->
            "   " +
                    presentableValue(element, product, contexts) +
                    (if (index == it.indices.last) "" else ",") +
                    (((element as Traceable).trace as? PsiTrace)?.psiElement?.containingFilename()
                        ?.let { formatSourceName(it) }
                        ?: "")
        }.joinToString("\n") +
        "\n]"

private fun PsiElement.containingFilename(): String? =
    ReadAction.compute<String, Throwable> {
        val containingFile = this.containingFile
        if (containingFile?.name == "module.yaml" || containingFile?.name == "module.amper") {
            (containingFile.parent?.name ?: "module").let {
                "${containingFile.name} ($it)"
            }
        } else containingFile?.name
    }
