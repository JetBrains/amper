/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.valueTracking

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.PlatformSpecific
import org.jetbrains.amper.frontend.api.ProductTypeSpecific
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.SchemaValuesVisitor
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.ValueBase
import org.jetbrains.amper.frontend.schema.ProductType
import kotlin.reflect.full.findAnnotation

private sealed class PropertyWithSource(
    val name: String,
    val applicablePlatforms: List<Platform>?,
    val applicableProductTypes: List<ProductType>?
) {
    class PropertyWithPrimitiveValue(name: String, val source: ValueSource?, val value: Any?,
                                     applicablePlatforms: List<Platform>? = null,
                                     applicableProductTypes: List<ProductType>? = null) : PropertyWithSource(name, applicablePlatforms, applicableProductTypes)
    class PropertyWithObjectValue(name: String, val value: List<PropertyWithSource>,
                                  applicablePlatforms: List<Platform>? = null,
                                  applicableProductTypes: List<ProductType>? = null) : PropertyWithSource(name, applicablePlatforms, applicableProductTypes)
}

sealed class ValueSource {
    data object Default: ValueSource()
    class Element(val element: PsiElement): ValueSource()
}

private class CollectingVisitor(private val properties: MutableList<PropertyWithSource>, private val contexts: Set<String>) : SchemaValuesVisitor() {
    override fun visitMap(it: Map<*, *>) {
        if (contexts.isNotEmpty()) {
            val matchingContext = it.keys.mapNotNull { it as? Set<*> }.firstOrNull {
                contexts.intersect(it.mapNotNull { (it as? TraceableString)?.value }).isNotEmpty()
            }
            if (matchingContext != null) {
                visit(it[matchingContext])
                return
            }
        }
        super.visitMap(it)
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun visitValue(valueBase: ValueBase<*>) {
        val applicablePlatforms = valueBase.property.findAnnotation<PlatformSpecific>()?.platforms?.toList()
        val applicableProductTypes = valueBase.property.findAnnotation<ProductTypeSpecific>()?.productTypes?.toList()
        val value = when {
            valueBase.value is SchemaNode -> {
                val innerProperties = mutableListOf<PropertyWithSource>()
                CollectingVisitor(innerProperties, contexts).visit(valueBase.value)
                PropertyWithSource.PropertyWithObjectValue(valueBase.property.name, innerProperties,
                    applicablePlatforms,
                    applicableProductTypes)
            }
            valueBase.value == null && valueBase.default != null -> {
                val innerProperties = mutableListOf<PropertyWithSource>()
                CollectingVisitor(innerProperties, contexts).visit(valueBase.default!!.value)
                PropertyWithSource.PropertyWithObjectValue(valueBase.property.name, innerProperties,
                    applicablePlatforms,
                    applicableProductTypes)
            }
            valueBase.value != null -> valueBase.value.let { value ->
                PropertyWithSource.PropertyWithPrimitiveValue(
                    valueBase.property.name,
                    (valueBase.trace as? PsiTrace)?.let { ValueSource.Element(it.psiElement) }
                        ?: valueBase.default?.takeIf { valueBase.trace == null && valueBase.value == valueBase.default!!.value }?.let { ValueSource.Default },
                    value,
                    applicablePlatforms,
                    applicableProductTypes
                )
            }
            else -> null
        }
        if (value != null) properties.add(value)
    }
}

private fun collectPropertiesWithSources(linkedValue: ValueBase<*>?, contexts: Set<Platform>): List<PropertyWithSource> {
    val properties = mutableListOf<PropertyWithSource>()
    linkedValue?.unsafe?.let { v ->
        CollectingVisitor(properties, contexts.map { it.pretty }.toSet()).visit(v)
    }
    return properties
}

@UsedInIdePlugin
fun tracesInfo(linkedValue: ValueBase<*>?, containingFile: PsiFile, productType: ProductType?, contexts: Set<Platform>): String {
    return (fullSectionInfo(linkedValue, containingFile, productType, contexts)
        ?.let {
            "\n\n${
                it.split("\n").joinToString("\n") { "> $it" }
            }"
        }
        ?: precedingValueTrace(linkedValue, containingFile) ?: "")
}

private fun fullSectionInfo(linkedValue: ValueBase<*>?, containingFile: PsiFile, product: ProductType?, contexts: Set<Platform>): String? {
    return renderObject(linkedValue, contexts, product, containingFile) ?: renderCollection(linkedValue, containingFile)
}

private fun renderObject(
    linkedValue: ValueBase<*>?,
    contexts: Set<Platform>,
    productType: ProductType?,
    containingFile: PsiFile,
): String? = collectPropertiesWithSources(linkedValue, contexts)
    .filter {
        (it.applicablePlatforms?.let { contexts.intersect(it).isNotEmpty() } ?: true)
                && (it.applicableProductTypes?.map { it.value }?.contains(productType?.value) ?: true)
    }
    .takeIf { it.isNotEmpty() }
    ?.prettyPrint(containingFile)

private fun renderCollection(linkedValue: ValueBase<*>?, containingFile: PsiFile): String? {
    val value = linkedValue?.value
    if (value is Collection<*>) {
        return presentableValue(value, containingFile)
    }
    return null
}

private fun List<PropertyWithSource>.prettyPrint(containingFile: PsiFile): String {
    return StringBuilder().also { printProperties(it, containingFile) }.toString()
}

private fun List<PropertyWithSource>.printProperties(
    builder: StringBuilder,
    containingFile: PsiFile
) {
    forEach {
        builder.append("**${it.name}**: ${
            when (it) {
                is PropertyWithSource.PropertyWithObjectValue -> it.value.prettyPrint(containingFile).let {
                    "\n" + it.split("\n").joinToString("\n") { "> $it" }
                }
                is PropertyWithSource.PropertyWithPrimitiveValue -> it.valueString(containingFile) + sourcePostfix(it, containingFile)
            }
        }\n\n")
    }
}

private fun sourcePostfix(
    it: PropertyWithSource.PropertyWithPrimitiveValue,
    containingFile: PsiFile,
): String {
    val sourceName = it.getSourceName(containingFile)
    return if (!sourceName.isNullOrBlank()) " [$sourceName]" else ""
}

private fun PropertyWithSource.PropertyWithPrimitiveValue.getSourceName(currentFile: PsiFile): String? = when (source) {
    ValueSource.Default -> "default"
    is ValueSource.Element -> getFileName(source.element, currentFile)
    null -> null
}

private fun PropertyWithSource.PropertyWithPrimitiveValue.valueString(containingFile: PsiFile): String
        = presentableValue(this.value, containingFile).let {
    if (this.value is Collection<*>) it
    else "*$it*"
}

private fun presentableValue(it: Any?, currentFile: PsiFile): String {
    return when {
        it is TraceableEnum<*> && it.value is SchemaEnum -> (it.value as SchemaEnum).schemaValue
        it is SchemaEnum -> it.schemaValue
        it is Collection<*> && it.isEmpty() -> "(empty)"
        it is Collection<*> && it.all { it is Traceable } -> "[\n\n" +
                it.joinToString(",\n\n") {
                    "> *" + presentableValue(it, currentFile) + "*" + ((it as Traceable).trace?.let {
                        (it as? PsiTrace)?.let { getFileName(it.psiElement, currentFile) }?.let { " [$it]" }
                    } ?: "")
                } +
                "\n\n]"
        it is Collection<*> -> "[" + it.joinToString { presentableValue(it, currentFile) } + "]"
        else -> it.toString()
    }
}

private fun getFileName(psiElement: PsiElement, ignoreIfFile: PsiFile? = null): String? = ReadAction.compute<String, Throwable> {
    val containingFile = psiElement.containingFile
    if (ignoreIfFile == containingFile) return@compute null
    if (containingFile?.name == "module.yaml" || containingFile?.name == "module.amper") {
        containingFile.parent?.name ?: "module"
    } else containingFile?.name
}

private fun precedingValueTrace(linkedValue: ValueBase<*>?, containingFile: PsiFile): String? {
    return linkedValue?.trace?.precedingValue?.let {
        val psiTrace = it.trace as? PsiTrace
        when {
            psiTrace == null && it.default?.value != null -> {
                "\n\n_Overrides default value **${presentableValue(it.default!!.value, containingFile)}**_"
            }
            psiTrace != null && it.value != linkedValue.value -> {
                getFileName(psiTrace.psiElement)?.let { fileName ->
                    "\n\n_Overrides value **${presentableValue(it.value, containingFile)}** from ${fileName}_"
                }
            }
            else -> ""
        }
    }
}