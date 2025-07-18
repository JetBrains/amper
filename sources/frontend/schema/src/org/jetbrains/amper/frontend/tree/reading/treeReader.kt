/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading


import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.util.asSafely
import com.intellij.util.containers.Stack
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.asTrace
import org.jetbrains.amper.frontend.api.asTraceable
import org.jetbrains.amper.frontend.api.trace
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.contexts.Context
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.tree.ListValue
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.Owned
import org.jetbrains.amper.frontend.tree.ReferenceValue
import org.jetbrains.amper.frontend.tree.ScalarValue
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.types.SchemaEnumDeclaration
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.simpleName
import org.jetbrains.amper.frontend.types.toType
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import java.nio.file.Path
import kotlin.io.path.absolute

internal fun BuildCtx.readTree(
    file: VirtualFile,
    type: SchemaObjectDeclaration,
    vararg contexts: Context,
    reportUnknowns: Boolean = true,
    parseReferences: Boolean = false,
) = TreeReadRequest(
    initialType = type,
    initialContexts = contexts.toSet(),
    file = file,
    psiFile = file.asPsi(),
    problemReporter = problemReporter,
    reportUnknowns = reportUnknowns,
    parseReferences = parseReferences,
).readTree()

/**
 * Conventional reading parameters grouping to prevent duplicating same
 * values for both [AmperLangTreeReader] and [YamlTreeReader].
 */
internal data class TreeReadRequest(
    val initialType: SchemaObjectDeclaration,
    val initialContexts: Contexts,
    val file: VirtualFile,
    val psiFile: PsiFile,
    val problemReporter: ProblemReporter,
    val reportUnknowns: Boolean,
    val parseReferences: Boolean,
)

// TODO Do we need the read action here?
internal fun TreeReadRequest.readTree(): MapLikeValue<*>? =
    ApplicationManager.getApplication().runReadAction(Computable {
        when (psiFile.language) {
//        is AmperLanguage -> AmperLangTreeReader(this).read()
            is YAMLLanguage -> YamlTreeReader(this).read()
                ?: Owned(emptyList(), initialType, psiFile.trace, initialContexts)

            else -> error("Unsupported language: ${psiFile.language}")
        }
    })

internal class ReaderCtx(params: TreeReadRequest) {
    val problemReporter = params.problemReporter
    private val baseDir = params.file.parent
    private val contextsStack = Stack<Contexts>().apply { push(params.initialContexts) }
    private val types = Stack<SchemaType>().apply { push(params.initialType.toType()) }
    private var currentValue: TreeValue<*>? = null
    private val currentContexts get() = contextsStack.lastOrNull().orEmpty()

    /**
     * Current type that is being read.
     */
    val currentType: SchemaType get() = types.last()

    // Convention constructor functions that are providing [currentContext].
    fun scalarValue(value: Any, trace: Trace) = ScalarValue<Owned>(value, trace, currentContexts)
    fun listValue(children: List<TreeValue<*>>, trace: Trace) = ListValue(children, trace, currentContexts)
    fun mapValue(children: List<MapLikeValue.Property<TreeValue<*>>>, trace: Trace) =
        Owned(children, (currentType as? SchemaType.ObjectType)?.declaration, trace, currentContexts)
    fun referenceValue(value: String, trace: Trace, prefix: String, suffix: String, type: SchemaType) =
        ReferenceValue<Owned>(value, trace, currentContexts, prefix = prefix, suffix = suffix, type = type)

    /**
     * Shortcut to catch the key in the trace also.
     */
    fun PsiElement.parentOrSelfTrace() = parent.asSafely<YAMLKeyValue>()?.asTrace() ?: asTrace()

    /**
     * Execute [block] and change [currentValue] to its return value.
     */
    fun readCurrent(block: ReaderCtx.() -> TreeValue<*>?) =
        run(block).let { currentValue = it }

    /**
     * Launch this visitor on passed [PsiElement]; Then return [ReaderCtx.currentValue] and clean it.
     */
    fun PsiElement.acceptAndGetCurrent(visitor: PsiElementVisitor) = try {
        accept(visitor)
        currentValue
    } finally {
        currentValue = null
    }

    /**
     * Try to read a value of the specified scalar [type] from the given [text] and return `null` if failed.
     */
    fun tryReadScalar(text: String, type: SchemaType.ScalarType, origin: PsiElement, report: Boolean = true): Any? {
        fun reportIfNeeded(msgId: String): Nothing? {
            if (report) {
                problemReporter.reportBundleError(source = origin.asBuildProblemSource(), messageKey = msgId)
            }
            return null
        }

        return when(type) {
            is SchemaType.StringType -> if (type.isTraceableWrapped) text.asTraceable(origin.trace) else text
            is SchemaType.IntType -> text.toIntOrNull() ?: reportIfNeeded("validation.expected.integer")
            is SchemaType.BooleanType -> text.toBooleanStrictOrNull() ?: reportIfNeeded("validation.expected.boolean")
            is SchemaType.PathType -> (baseDir.resolveOrNull(text) ?: reportIfNeeded("validation.expected.path")).let {
                if (type.isTraceableWrapped) it?.asTraceable(origin.trace) else it
            }
            is SchemaType.EnumType -> tryReadEnum(text, type.declaration, origin, report).let {
                if (type.isTraceableWrapped) {
                    it as Enum<*>?
                    it?.asTraceable(origin.trace)
                } else it
            }
        }
    }

    /**
     * Try to resolve an absolute path and return `null` if `part` is invalid.
     */
    // FIXME VirtualFile vs Path.
    private fun VirtualFile.resolveOrNull(part: String): Path? =
        part.toNioPathOrNull()?.let { toNioPathOrNull()?.resolve(it)?.absolute()?.normalize() }

    private fun String.splitByCamelHumps() = sequence {
        onEach { if (it.isUpperCase()) yield(' ') }.forEach { yield(it.lowercase()) }
    }.joinToString("").trimStart()

    /**
     * Try to read value of a specified [enum] from the [YAMLScalar] and return `null` if failed.
     */
    private fun tryReadEnum(text: String, enum: SchemaEnumDeclaration, origin: PsiElement, report: Boolean): Any? {
        val values = enum.entries
        val selectedEntry = values.firstOrNull { it.schemaValue == text }
        if (selectedEntry == null && report) {
            problemReporter.reportBundleError(
                source = origin.asBuildProblemSource(),
                messageKey = if (values.size > 10) "validation.unknown.enum.value.short" else "validation.unknown.enum.value",
                enum.simpleName().splitByCamelHumps(),
                text,
                values.joinToString { it.schemaValue },
                buildProblemId = "validation.unknown.enum.value",
            )
        }
        return selectedEntry?.name?.let(enum::toEnumConstant) ?: selectedEntry?.name
    }

    fun <R> withNew(type: SchemaType? = null, contexts: Collection<Context> = emptySet(), block: () -> R) =
        types.pushAndPop(type) {
            val newCtx = currentContexts.map(Context::withoutTrace).plus(contexts).ifEmpty { null }
            this@ReaderCtx.contextsStack.pushAndPop(newCtx, block)
        }

    private inline fun <T : Any, R> Stack<T>.pushAndPop(value: T?, block: () -> R) =
        if (value == null) block() else try {
            push(value)
            block()
        } finally {
            pop()
        }
}