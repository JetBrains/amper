/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.valueTracking

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.Default
import org.jetbrains.amper.frontend.api.PlatformSpecific
import org.jetbrains.amper.frontend.api.ProductTypeSpecific
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.SchemaValuesVisitor
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.ValueBase
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.frontend.schema.ProductType
import kotlin.reflect.full.findAnnotation

private sealed class PropertyWithSource(
    val name: String,
    val applicablePlatforms: List<Platform>?,
    val applicableProductTypes: List<ProductType>?
) {
    class PropertyWithPrimitiveValue(
        name: String, val source: ValueSource?, val value: Any?,
        applicablePlatforms: List<Platform>? = null,
        applicableProductTypes: List<ProductType>? = null
    ) : PropertyWithSource(name, applicablePlatforms, applicableProductTypes)

    class PropertyWithObjectValue(
        name: String, val value: List<PropertyWithSource>,
        applicablePlatforms: List<Platform>? = null,
        applicableProductTypes: List<ProductType>? = null
    ) : PropertyWithSource(name, applicablePlatforms, applicableProductTypes)
}

sealed class ValueSource {
    data object Default : ValueSource()
    class DependentDefault(val desc: String, val element: PsiElement?): ValueSource()
    class Element(val element: PsiElement) : ValueSource()
}

private class CollectingVisitor(
    private val properties: MutableList<PropertyWithSource>,
    private val contexts: Set<String>
) : SchemaValuesVisitor() {
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
                PropertyWithSource.PropertyWithObjectValue(
                    valueBase.property.name, innerProperties,
                    applicablePlatforms,
                    applicableProductTypes
                )
            }

            valueBase.value == null && valueBase.default != null -> {
                val innerProperties = mutableListOf<PropertyWithSource>()
                CollectingVisitor(innerProperties, contexts).visit(valueBase.default!!.value)
                PropertyWithSource.PropertyWithObjectValue(
                    valueBase.property.name, innerProperties,
                    applicablePlatforms,
                    applicableProductTypes
                )
            }

            valueBase.value != null -> valueBase.value.let { value ->
                PropertyWithSource.PropertyWithPrimitiveValue(
                    valueBase.property.name,
                    (valueBase.trace as? PsiTrace)?.let { ValueSource.Element(it.psiElement) }
                        ?: valueBase.default?.takeIf { valueBase.value == valueBase.default!!.value }
                            ?.let { def ->
                                if (def is Default.Dependent<*, *>) {
                                    ValueSource.DependentDefault(def.desc,
                                        def.property.valueBase?.extractPsiElementOrNull())
                                }
                                else ValueSource.Default.takeIf { valueBase.trace == null }
                            },
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

enum class TracesPresentation {
    IDE,
    CLI,
    Tests
}

@UsedInIdePlugin
fun tracesInfo(
    linkedValue: ValueBase<*>?,
    containingFile: PsiFile?,
    productType: ProductType?,
    contexts: Set<Platform>,
    presentation: TracesPresentation = TracesPresentation.IDE
): String {
    return (compositeValueTracesInfo(linkedValue?.unsafe, containingFile, productType, contexts, presentation)
        ?.let { prependPrefix(it, presentation) }
        ?: precedingValueTrace(linkedValue, containingFile, presentation) ?: "")
}

fun compositeValueTracesInfo(
    value: Any?,
    containingFile: PsiFile?,
    product: ProductType?,
    contexts: Set<Platform>,
    presentation: TracesPresentation
): String? {
    return renderObject(value, contexts, product, containingFile, presentation)
        ?: renderCollection(value, containingFile, presentation)
}

private fun renderObject(
    value: Any?,
    contexts: Set<Platform>,
    productType: ProductType?,
    containingFile: PsiFile?,
    presentation: TracesPresentation,
): String? = mutableListOf<PropertyWithSource>().also {
    value?.let { v ->
        CollectingVisitor(it, contexts.map { it.pretty }.toSet()).visit(v)
    }
}
    .filter {
        (it.applicablePlatforms?.let { contexts.intersect(it).isNotEmpty() } ?: true)
                && (it.applicableProductTypes?.map { it.value }?.contains(productType?.value) ?: true)
    }
    .takeIf { it.isNotEmpty() }
    ?.prettyPrint(containingFile, presentation)

private fun renderCollection(value: Any?, containingFile: PsiFile?, presentation: TracesPresentation): String? {
    if (value is Collection<*>) {
        return presentableValue(value, containingFile, presentation)
    }
    return null
}

private fun List<PropertyWithSource>.prettyPrint(containingFile: PsiFile?, presentation: TracesPresentation): String {
    return StringBuilder().also { printProperties(it, containingFile, presentation) }.toString()
}

private fun List<PropertyWithSource>.printProperties(
    builder: StringBuilder,
    containingFile: PsiFile?,
    presentation: TracesPresentation
) {
    forEach { prop ->
        builder.append(
            "${wrapName(prop, presentation)}: ${
            when (prop) {
                is PropertyWithSource.PropertyWithObjectValue -> prop.value.prettyPrint(containingFile, presentation).let {
                    "\n" + prependPrefix(it, presentation)
                }

                is PropertyWithSource.PropertyWithPrimitiveValue -> {
                    val value = prop.value
                    presentableValue(value, containingFile, presentation).let {
                        if (value is Collection<*>) it
                        else presentation.wrapValue(it)
                    } + sourcePostfix(prop, containingFile, presentation).let { 
                        it.takeIf { value !is Collection<*> || value.isEmpty() } ?: ""
                    }
                }
            }
        }${presentation.sectionSeparator}")
    }
}

private val TracesPresentation.sectionSeparator get() = when (this) {
    TracesPresentation.IDE -> "\n\n"
    else -> "\n"
}

private val TracesPresentation.prefix get() = when (this) {
    TracesPresentation.IDE -> ">"
    else -> "  "
}

private fun TracesPresentation.wrapValue(value: String) = when (this) {
    TracesPresentation.IDE -> "*$value*"
    else -> value
}

private fun prependPrefix(string: String, presentation: TracesPresentation): String {
    return string.split("\n").joinToString("\n") { "${presentation.prefix} $it" }
}

private fun wrapName(source: PropertyWithSource, presentation: TracesPresentation): String =
    when (presentation) {
        TracesPresentation.IDE -> "**${source.name}**"
        else -> source.name
    }

private fun formatSourceName(sourceName: String, presentation: TracesPresentation): String {
    return when (presentation) {
        TracesPresentation.CLI -> "  # [$sourceName]"
        else -> " [$sourceName]"
    }
}

private fun sourcePostfix(
    it: PropertyWithSource.PropertyWithPrimitiveValue,
    containingFile: PsiFile?,
    presentation: TracesPresentation
): String {
    val sourceName = when (it.source) {
        ValueSource.Default -> "default"
        is ValueSource.DependentDefault -> it.source.desc + (it.source.element?.let { element ->
            getFileName(element, containingFile, presentation)?.let {
                if (it.isNotBlank()) " @ $it" else ""
            }
        }.orEmpty())
        is ValueSource.Element -> getFileName(it.source.element, containingFile, presentation)
        null -> null
    }
    return if (!sourceName.isNullOrBlank()) formatSourceName(sourceName, presentation) else ""
}

private fun presentableValue(it: Any?, currentFile: PsiFile?, presentation: TracesPresentation): String {
    return when {
        it is TraceableEnum<*> && it.value is SchemaEnum -> (it.value as SchemaEnum).schemaValue
        it is SchemaEnum -> it.schemaValue
        it is Collection<*> && it.isEmpty() -> if (presentation != TracesPresentation.CLI) "(empty)" else "[]"
        it is Collection<*> && it.all { it is Traceable } -> renderTraceableCollection(it, currentFile, presentation)
        it is Collection<*> -> "[" + it.joinToString { presentableValue(it, currentFile, presentation) } + "]"
        else -> it.toString()
    }
}

private fun renderTraceableCollection(
    it: Collection<*>,
    currentFile: PsiFile?,
    presentation: TracesPresentation
): String = "[${presentation.sectionSeparator}" +
        it.mapIndexed { index, element ->
            "${presentation.prefix} " +
                    presentation.wrapValue(presentableValue(element, currentFile, presentation)) +
                    (if (presentation != TracesPresentation.CLI || index == it.size - 1) "" else ",") +
                     ((element as Traceable).trace?.let {
                (it as? PsiTrace)?.let { getFileName(it.psiElement, currentFile, presentation) }?.let { formatSourceName(it, presentation) }
            } ?: "") +
                    (if (presentation == TracesPresentation.CLI || index == it.size - 1) "" else ",")
        }.joinToString(presentation.sectionSeparator) +
        "${presentation.sectionSeparator}]"

private fun getFileName(psiElement: PsiElement, ignoreIfFile: PsiFile? = null, presentation: TracesPresentation): String? =
    ReadAction.compute<String, Throwable> {
        val containingFile = psiElement.containingFile
        if (ignoreIfFile == containingFile) return@compute null
        if (containingFile?.name == "module.yaml" || containingFile?.name == "module.amper") {
            (containingFile.parent?.name ?: "module").let {
                if (presentation == TracesPresentation.CLI) "${containingFile.name} ($it)" else it
            }
        } else containingFile?.name
    }

private fun precedingValueTrace(linkedValue: ValueBase<*>?, containingFile: PsiFile?, presentation: TracesPresentation): String? {
    return linkedValue?.trace?.precedingValue?.let {
        val psiTrace = it.trace as? PsiTrace
        when {
            psiTrace == null && it.default?.value != null -> {
                presentation.sectionSeparator +
                        SchemaBundle.message("tracing.overrides.default", presentation.wrapValue(presentableValue(it.default!!.value, containingFile, presentation)))
            }

            psiTrace != null && it.value != linkedValue.value -> {
                getFileName(psiTrace.psiElement, presentation = presentation)?.let { fileName ->
                    presentation.sectionSeparator +
                            SchemaBundle.message("tracing.overrides", presentation.wrapValue(presentableValue(it.value, containingFile, presentation)), fileName)
                }
            }

            else -> ""
        }
    }
}