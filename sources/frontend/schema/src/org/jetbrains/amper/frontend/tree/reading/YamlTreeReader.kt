/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.asSafely
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.asTrace
import org.jetbrains.amper.frontend.api.trace
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.contexts.PlatformCtx
import org.jetbrains.amper.frontend.contexts.TestCtx
import org.jetbrains.amper.frontend.diagnostics.helpers.extractKeyElement
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.BomDependency
import org.jetbrains.amper.frontend.schema.CatalogBomDependency
import org.jetbrains.amper.frontend.schema.CatalogDependency
import org.jetbrains.amper.frontend.schema.CatalogJavaAnnotationProcessorDeclaration
import org.jetbrains.amper.frontend.schema.CatalogKspProcessorDeclaration
import org.jetbrains.amper.frontend.schema.Dependency
import org.jetbrains.amper.frontend.schema.ExternalMavenBomDependency
import org.jetbrains.amper.frontend.schema.ExternalMavenDependency
import org.jetbrains.amper.frontend.schema.InternalDependency
import org.jetbrains.amper.frontend.schema.JavaAnnotationProcessorDeclaration
import org.jetbrains.amper.frontend.schema.KspProcessorDeclaration
import org.jetbrains.amper.frontend.schema.MavenJavaAnnotationProcessorDeclaration
import org.jetbrains.amper.frontend.schema.MavenKspProcessorDeclaration
import org.jetbrains.amper.frontend.schema.ModuleJavaAnnotationProcessorDeclaration
import org.jetbrains.amper.frontend.schema.ModuleKspProcessorDeclaration
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.NoValue
import org.jetbrains.amper.frontend.tree.Owned
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.tree.owned
import org.jetbrains.amper.frontend.types.ATypes
import org.jetbrains.amper.frontend.types.ATypes.AList
import org.jetbrains.amper.frontend.types.ATypes.AMap
import org.jetbrains.amper.frontend.types.ATypes.AObject
import org.jetbrains.amper.frontend.types.ATypes.APolymorphic
import org.jetbrains.amper.frontend.types.ATypes.AType
import org.jetbrains.amper.frontend.types.isBoolean
import org.jetbrains.amper.frontend.types.isEnum
import org.jetbrains.amper.frontend.types.isString
import org.jetbrains.amper.frontend.types.isSubclassOf
import org.jetbrains.amper.frontend.types.kClassOrNull
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YamlPsiElementVisitor
import kotlin.reflect.KClass


/**
 * Implementation of [YamlPsiElementVisitor] that is building [TreeValue] representation
 * of any amper YAML file.
 */
internal class YamlTreeReader(val params: TreeReadRequest) : YamlPsiElementVisitor() {
    private val ctx = ReaderCtx(params)
    private fun PsiElement.acceptAndGet() = with(ctx) { acceptAndGetCurrent(this@YamlTreeReader) }
    fun read(): MapLikeValue<Owned>? = params.psiFile.acceptAndGet() as? MapLikeValue<Owned>

    override fun visitMapping(mapping: YAMLMapping) = ctx.readCurrent {
        when (val type = currentType) {
            is AMap -> tryReadAsMap(type, mapping.keyValues, mapping) // TODO Report if failed to read.
            is AObject -> tryReadAsObject(type, mapping.keyValues, mapping) // TODO Report if failed to read.
            is APolymorphic -> tryReadAsPolyFromKeyValues(type, mapping.keyValues) // TODO Report if failed to read.
            else -> null // TODO Report.
        }
    }

    /**
     * Supports a special case in the YAML file, when a list can be treated like a map.
     * Example:
     * ```
     * aliases:
     *   - alias1: [ foo, bar ]
     *   - alias2: [ foo, baz ]
     * ```
     */
    override fun visitSequence(sequence: YAMLSequence) = ctx.readCurrent {
        when (val type = currentType) {
            is AList -> tryReadAsList(type, sequence) // TODO Report if failed to read.
            is AMap -> sequence.items.mapNotNull { it.value }
                .filterIsInstance<YAMLMapping>()
                .mapNotNull { it.keyValues.singleOrNull() }
                .takeIf { sequence.items.size == it.size }
                ?.let { tryReadAsMap(type, it, sequence) }

            else -> null // TODO Report wrong type.
        }
    }

    override fun visitScalar(scalar: YAMLScalar) = ctx.readCurrent {
        tryReadAsShorthand(scalar)
            ?: when (val type = currentType) {
                is APolymorphic -> tryReadAsPolyFromScalar(type, scalar)
                else -> tryReadScalar(scalar.textValue, currentType.kType, scalar)
                    // We explicitly here construct trace from that parent to catch also the key. 
                    ?.let { scalarValue(it, scalar.parentOrSelfTrace()) }
            }
    }

    override fun visitElement(element: PsiElement) {
        ProgressIndicatorProvider.checkCanceled()
        element.acceptChildren(this)
    }

    private fun ReaderCtx.tryReadAsPolyFromKeyValues(type: APolymorphic, keyValues: Collection<YAMLKeyValue>) =
        keyValues.singleOrNull()?.let {
            val (keyText, contexts) = extractContexts(it)
            withNew(contexts = contexts) { tryReadAsPolyDependency(type, keyText, it) }
        }

    private fun ReaderCtx.tryReadAsPolyFromScalar(type: APolymorphic, scalar: YAMLScalar) =
        tryReadAsPolyDependency(type, scalar.textValue, scalar)

    // Triple elements order matters!
    private val supportedPolyTypes = mapOf(
        "plain" to Triple(
            InternalDependency::class,
            CatalogDependency::class,
            ExternalMavenDependency::class
        ),
        "ksp" to Triple(
            ModuleKspProcessorDeclaration::class,
            CatalogKspProcessorDeclaration::class,
            MavenKspProcessorDeclaration::class
        ),
        // Java annotation processors.
        "jap" to Triple(
            ModuleJavaAnnotationProcessorDeclaration::class,
            CatalogJavaAnnotationProcessorDeclaration::class,
            MavenJavaAnnotationProcessorDeclaration::class,
        ),
    )

    private fun ReaderCtx.tryReadAsPolyDependency(
        type: APolymorphic,
        keyText: String,
        currentElement: PsiElement,
    ): MapLikeValue<Owned>? {
        // Currently, the only discriminator property is one marked with [DependencyKey].
        // Thus, object type resolving is now hardcoded only for dependencies.
        if (!type.inheritors.all { it.properties.singleOrNull { it.meta.isCtorArg } != null })
            return null // TODO Report other than dependencies are unsupported.

        val (moduleDepType, catalogDepType, plainDepType) = when {
            type.kType.isSubclassOf<Dependency>() -> supportedPolyTypes.getValue("plain")
            type.kType.isSubclassOf<KspProcessorDeclaration>() -> supportedPolyTypes.getValue("ksp")
            type.kType.isSubclassOf<JavaAnnotationProcessorDeclaration>() -> supportedPolyTypes.getValue("jap")
            else -> error("Unsupported type: ${type.kType}")
        }

        fun <T : Any> APolymorphic.findInheritor(klass: KClass<T>) =
            inheritors.singleOrNull { it.kType.kClassOrNull.isSubclassOf(klass) }

        data class Grouped(
            val type: AObject,
            val ctorText: String,
            val ctorElement: PsiElement,
            val childElement: PsiElement?,
        )

        // TODO Need to rethink this from AmperLang constructors perspective.
        val (newType, ctorText, ctorElement, childElement) = if (keyText == "bom" && type.kType.isSubclassOf<Dependency>()) {
            // TODO Report.
            val notationElement = currentElement.asSafely<YAMLKeyValue>()?.value?.asSafely<YAMLScalar>() ?: return null
            val notation = notationElement.text
            val type = when {
                notation.startsWith("$") -> type.findInheritor(CatalogBomDependency::class)
                else -> type.findInheritor(ExternalMavenBomDependency::class)
            } ?: error("Should be unreachable")
            Grouped(type, notation, notationElement, null)
        } else {
            val type = when {
                keyText.startsWith(".") -> type.findInheritor(moduleDepType)
                keyText.startsWith("$") -> type.findInheritor(catalogDepType)
                else -> type.findInheritor(plainDepType)
            } ?: error("Should be unreachable")
            // Ctor can be just a YAMLScalar, thus we can't tell here for sure.
            val ctorElement = currentElement.asSafely<YAMLKeyValue>()?.key ?: currentElement
            Grouped(type, keyText, ctorElement, currentElement.asSafely<YAMLKeyValue>()?.value)
        }

        return doTryReadAsPoly(newType, ctorText, ctorElement, currentElement, childElement)
    }

    private fun ReaderCtx.doTryReadAsPoly(
        type: AObject,
        ctorText: String,
        ctorElement: PsiElement,
        parentElement: PsiElement,
        childElement: PsiElement? = null,
    ): MapLikeValue<Owned>? {
        // FIXME Replace all errors with reports.
        val ctorProperty = type.properties.singleOrNull { it.meta.isCtorArg } ?: error("Should be unreachable")
        val ctorScalar = tryReadScalar(ctorText, ctorProperty.type.kType, ctorElement) ?: return null
        val ctorValue = scalarValue(ctorScalar, ctorElement.trace)
        return withNew(type) {
            val otherChildren = childElement?.acceptAndGet().asSafely<MapLikeValue<Owned>>()?.children
            val ctorProperty = MapLikeValue.Property(ctorValue, ctorElement.parent.trace, ctorProperty)
            mapValue(listOf(ctorProperty) + otherChildren.orEmpty(), parentElement.trace)
        }
    }

    private fun ReaderCtx.tryReadAsShorthand(scalar: YAMLScalar): TreeValue<Owned>? {
        val lastObjectType = currentType.asSafely<AObject>() ?: return null
        if (!lastObjectType.hasShorthands) return null
        val shorthandAware = lastObjectType.properties.filter { it.meta.hasShorthand }.sortedBy {
            // Booleans have priority.
            if (it.meta.type.isBoolean) 0
            else if (it.meta.type.isEnum) 1
            else 2
        }

        val candidates = shorthandAware.mapNotNull {
            // `compose: enabled` means that we should set `compose.enabled` setting as `true`.
            val pType = it.meta.type
            if (pType.isBoolean && scalar.textValue in it.meta.nameAndAliases) true to it
            // `kotlin.serialization: json` means that we should set the `serialization.format` setting as `json`.
            else if (pType.isString || pType.isEnum) tryReadScalar(scalar.textValue, pType, scalar, false)
                ?.to(it)
            else null
        }
        // TODO Maybe we need to rework shorthands completely.
        val readShorthand = candidates.firstOrNull()
        return if (readShorthand != null) {
            val value = scalarValue(readShorthand.first, scalar.asTrace())
            mapValue(listOf(MapLikeValue.Property(value, scalar.asTrace(), readShorthand.second)), scalar.asTrace())
        } else null
    }

    private fun ReaderCtx.tryReadAsList(type: AList, sequence: YAMLSequence) = sequence.items
        .mapNotNull { withNew(type = type.valueType) { it.value?.acceptAndGet() } }
        .let { listValue(it, sequence.parentOrSelfTrace()) }

    private fun ReaderCtx.tryReadAsMap(type: AMap, keyValues: Collection<YAMLKeyValue>, origin: PsiElement) =
        tryReadAsObjectOrMap(keyValues, origin) { pName, _ -> type.valueType to null }

    private fun ReaderCtx.tryReadAsObject(type: AObject, keyValues: Collection<YAMLKeyValue>, origin: PsiElement) =
        tryReadAsObjectOrMap(keyValues, origin) out@{ pName, pOrigin ->
            val prop = type.aliased[pName] ?: return@out if (params.reportUnknowns) pOrigin.reportAndNull(
                "unknown.property",
                pName
            ) else null
            prop.type to prop
        }

    val knownTestBlocks = mapOf(
        "test-settings" to Pair("settings", true),
        "test-dependencies" to Pair("dependencies", true),
    )

    private fun ReaderCtx.tryReadAsObjectOrMap(
        keyValues: Collection<YAMLKeyValue>,
        origin: PsiElement,
        childType: (String, PsiElement) -> Pair<AType, ATypes.AProperty?>?,
    ): MapLikeValue<Owned>? {
        val properties = keyValues.mapNotNull {
            val (key, contexts) = extractContexts(it)
            val (adjustedKey, testCtxNeeded) = knownTestBlocks.getOrElse(key) { key to false }
            val (childType, pType) = childType(adjustedKey, it.key!!) ?: return@mapNotNull null
            val newContexts = if (testCtxNeeded) contexts + TestCtx(origin.trace) else contexts
            withNew(childType, newContexts) {
                MapLikeValue.Property(
                    adjustedKey,
                    (it.key ?: it).asTrace(),
                    it.value?.acceptAndGet() ?: NoValue.owned,
                    pType,
                )
            }
        }
        return mapValue(properties, origin.parentOrSelfTrace())
    }

    private fun ReaderCtx.extractContexts(keyValue: YAMLKeyValue): Pair<String, Contexts> {
        // Adding empty string to an array so we won't fail on destructuring.
        val (readKey, ctxStr) = keyValue.keyText.split('@', limit = 2) + ""
        val multipleQualifiers = ctxStr.split("+")
        // Currently, multiple modifiers are not supported by AOM.
        // TODO Rethink - is it true?
        if (multipleQualifiers.size > 1) SchemaBundle.reportBundleError(
            keyValue.key!!,
            "multiple.qualifiers.are.unsupported"
        )
        return readKey to multipleQualifiers
            .map(String::trim)
            .filter { it.isNotEmpty() }
            // FIXME Implement more granular PSI traces, that can point to a part of PSIElement.
            .map { PlatformCtx(it, keyValue.trace) }
            .toSet()
    }
}