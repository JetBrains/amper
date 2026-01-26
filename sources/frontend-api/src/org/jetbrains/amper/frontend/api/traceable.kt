/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.api

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.util.asSafely
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.tree.TreeNode
import java.nio.file.Path

/**
 * A trace is a backreference that allows to determine the source of the model property/value.
 * It can be a PSI element, or a synthetic trace in case the property has been constructed programmatically.
 */
sealed interface Trace {
    /**
     * Old value defined initially and suppressed by merge or inheritance during model building,
     * it could be a chain of the subsequent preceding values built during merge of templates and module file definitions.
     */
    val precedingValue: TreeNode?
}

/**
 * Whether the value with this [Trace] can be considered a default value.
 * Hint: this is not obvious, be careful what you actually want here.
 */
val Trace.isDefault: Boolean
    get() = when (this) {
        is BuiltinCatalogTrace -> true
        is DefaultTrace -> true
        is DerivedValueTrace -> definitionTrace.isDefault
        is PsiTrace -> false
    }

/**
 * The file within which this trace points to, or null if this trace doesn't point to a file (or a place inside a file).
 */
val Trace.containingFile: VirtualFile?
    get() = when(this) {
        is PsiTrace -> psiElement.containingFile.originalFile.virtualFile
        is DefaultTrace -> null
        is BuiltinCatalogTrace -> null
        is DerivedValueTrace -> definitionTrace.containingFile
    }

/**
 * Whether this trace points to a template file.
 */
val Trace.isFromTemplate: Boolean
    get() = containingFile?.let { ".module-template." in it.name } ?: false

/**
 * A trace indicating that the value is a static default value (hardcoded in the schema, or in the code).
 */
data object DefaultTrace : Trace {
    override val precedingValue: TreeNode?
        get() = null // doesn't override anything because it's a default - it was here first!
}

/**
 * The value with this trace originates from a [psiElement].
 */
data class PsiTrace(
    /**
     * The [PsiElement] that this value was read from.
     */
    val psiElement: PsiElement,
    override val precedingValue: TreeNode? = null,
) : Trace

/**
 * Creates a [PsiTrace] that points to this [PsiElement].
 */
fun PsiElement.asTrace(): PsiTrace = PsiTrace(this)

/**
 * The value with this trace originates from a built-in version catalog provided by a toolchain.
 */
@UsedInIdePlugin // just to filter the catalog entries, no property is used from this type
data class BuiltinCatalogTrace(
    val catalog: VersionCatalog,
    val version: TraceableVersion,
) : Trace {
    override val precedingValue: TreeNode? get() = null
}

/**
 * A trace indicating that the value was derived from another value.
 *
 * It can describe a value that was directly copied from some source (a resolved reference) or a value that results
 * from the transformation of some source value.
 *
 * The [definitionTrace] is the trace to where the "derivation" (transformation / reference) itself is defined (e.g. a
 * literal reference element like `${module.name}` in the module file). If it's defined directly in Amper's code, we
 * use the [DefaultTrace] as the [definitionTrace].
 *
 * The [sourceValue] is the input value that is copied or transformed into the final value.
 *
 * Example: for the final value of `someProp`, the trace will be a [DerivedValueTrace] configured this way:
 *
 * ```yaml
 * settings:
 *   kotlin:
 *     serialization:
 *       format: json  # 'sourceValue' points here, to where the value comes from
 *   myPlugin:
 *     someProp: ${settings.kotlin.serialization.format} # 'definitionTrace' points here, where the ref is defined
 * ```
 *
 * @see ResolvedReferenceTrace
 * @see TransformedValueTrace
 */
sealed interface DerivedValueTrace : Trace {
    /**
     * A short description of how we obtained the value from the source value.
     *
     * Its purpose is to be used in some introspection commands / results. Typically, in the `show settings` CLI
     * command, this description is used in the form of a comment next to the values to explain where they come from.
     * As a result, here are a few guidelines to respect:
     *
     * * use just a few words, not full sentences
     * * keep it on a single line
     * * start with a lowercase letter (so it fits nicely in parentheses)
     *
     * Examples: "default", "because Compose is enabled", "from ${settings.kotlin.version}"
     */
    val description: String
    /**
     * The trace to the place where the reference or transformation is defined.
     *
     * For literal references in YAML files like `${settings.kotlin.version}`, this should be a [PsiTrace] pointing to
     * the reference itself (the text with the `$`).
     * For more abstract transformation definitions, this should represent where the transformation itself is defined.
     *
     * For example, some properties in the schema take their default value from another property when they're not set.
     * In this case, the reference is implicitly defined in the schema as the default value, so its [definitionTrace]
     * should be the [DefaultTrace].
     *
     * Another example is a set of Maven coordinates defined in code, derived from a version value defined in the
     * settings. In this case, the [definitionTrace] is also the [DefaultTrace] because it's defined in the code, and
     * the source value is the traceable version from the settings.
     */
    val definitionTrace: Trace
    /**
     * The traceable source value from which this trace's value was derived.
     *
     * For references, this should trace to the value that was resolved by following the reference.
     * For example, if the reference is `${settings.kotlin.version}`, the [sourceValue] should trace to the value
     * in the module file that the `settings.kotlin.version` was set to (or [DefaultTrace] if the property wasn't set
     * explicitly).
     *
     * For more complicated computed values, this should trace to the value that is used as input for the computation
     * or transformation. For example, if we construct Maven coordinates in the code from a default groupId and
     * artifactId, but from a version that comes from a setting, the trace's [sourceValue] is the trace to the version
     * property's value, while the [definitionTrace] is the [DefaultTrace] (concatenation defined in code).
     */
    // TODO ideally we should make it a `TraceableValue<T>` and maybe make DerivedValueTrace generic in T.
    //  Not possible now because we put a lot of schemaDelegate references here, and they are not yet TraceableValue
    //  themselves.
    val sourceValue: Traceable
}

/**
 * A trace indicating that the value is the result of transforming another value.
 *
 * This can be understood in a very wide sense. For example, if a list item is added by default because a property
 * somewhere is set to a specific value (e.g. enabled=true), the trace of this item can be seen as a
 * [TransformedValueTrace] that takes its source in the property value, and with a [DefaultTrace] as the
 * [definitionTrace].
 */
data class TransformedValueTrace(
    override val description: String,
    override val sourceValue: Traceable,
    override val definitionTrace: Trace = DefaultTrace,
    override val precedingValue: TreeNode? = null,
) : DerivedValueTrace

/**
 * A trace indicating that the value was resolved from a reference.
 *
 * This also applies to defaults that take their value directly from some other property, because the default value
 * is conceptually a reference to the other property.
 */
data class ResolvedReferenceTrace(
    override val description: String,
    /**
     * Trace to where the reference itself is defined.
     *
     * Can be [DefaultTrace] for default values that are directly copied from other properties (the default value can
     * be seen as a hardcoded "reference").
     */
    val referenceTrace: Trace,
    /**
     * The traceable value that was resolved from the reference (or copied from the source, in a wider sense).
     */
    val resolvedValue: Traceable,
    override val precedingValue: TreeNode? = null,
) : DerivedValueTrace {
    override val definitionTrace: Trace get() = referenceTrace
    override val sourceValue: Traceable get() = resolvedValue
}

fun Trace.withPrecedingValue(precedingValue: TreeNode): Trace = when (this) {
    is PsiTrace -> copy(precedingValue = precedingValue)
    is ResolvedReferenceTrace -> copy(precedingValue = precedingValue)
    is TransformedValueTrace -> copy(precedingValue = precedingValue)
    is BuiltinCatalogTrace -> error("Built-in catalog entries shouldn't override anything")
    is DefaultTrace -> error("Defaults shouldn't override anything")
}

/**
 * An entity that can persist its trace.
 */
interface Traceable {

    @property:IgnoreForSchema
    val trace: Trace
}

/**
 * A value that can persist its trace.
 */
open class TraceableValue<T>(val value: T, override val trace: Trace) : Traceable {
    override fun toString() = value.toString()
    override fun hashCode() = value.hashCode()
    override fun equals(other: Any?) = this === other || other?.asSafely<TraceableValue<*>>()?.value == value
}

open class TraceableString(value: String, trace: Trace) : TraceableValue<String>(value, trace)

/**
 * Marker type to find which derived value is the version in DR diagnostics.
 */
class TraceableVersion(value: String, trace: Trace) : TraceableString(value, trace)

class TraceablePath(value: Path, trace: Trace) : TraceableValue<Path>(value, trace)

/**
 * When the enum value isn't wrapped into the schema value (e.g., in a collection or in AOM),
 * it's impossible to determine the trace of that enum.
 *
 * This wrapper allows persisting a trace in such scenarios.
 */
class TraceableEnum<T : Enum<*>>(value: T, trace: Trace) : TraceableValue<T>(value, trace) {

    override fun toString(): String = value.toString()
}

fun <T : Enum<*>> T.asTraceable(trace: Trace) = TraceableEnum(this, trace)
fun Path.asTraceable(trace: Trace) = TraceablePath(this, trace)
fun String.asTraceable(trace: Trace) = TraceableString(this, trace)
