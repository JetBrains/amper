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
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.asTrace
import org.jetbrains.amper.frontend.api.asTraceable
import org.jetbrains.amper.frontend.api.trace
import org.jetbrains.amper.frontend.contexts.Context
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.tree.ListValue
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.Owned
import org.jetbrains.amper.frontend.tree.PsiReporterCtx
import org.jetbrains.amper.frontend.tree.ScalarValue
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.types.AmperTypes
import org.jetbrains.amper.frontend.types.AmperTypes.AmperType
import org.jetbrains.amper.frontend.types.enumValuesOrNull
import org.jetbrains.amper.frontend.types.isBoolean
import org.jetbrains.amper.frontend.types.isEnum
import org.jetbrains.amper.frontend.types.isInt
import org.jetbrains.amper.frontend.types.isPath
import org.jetbrains.amper.frontend.types.isString
import org.jetbrains.amper.frontend.types.isTraceableEnum
import org.jetbrains.amper.frontend.types.isTraceablePath
import org.jetbrains.amper.frontend.types.isTraceableString
import org.jetbrains.amper.frontend.types.kClass
import org.jetbrains.amper.frontend.types.traceableType
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.reflect.KType
import kotlin.reflect.full.starProjectedType


internal fun BuildCtx.readTree(
    file: VirtualFile,
    type: AmperTypes.Object,
    vararg contexts: Context,
    reportUnknowns: Boolean = true
) = TreeReadRequest(
    initialType = type,
    initialContexts = contexts.toSet(),
    file = file,
    psiFile = file.asPsi(),
    problemCtx = this,
    reportUnknowns = reportUnknowns
).readTree()

/**
 * Conventional reading parameters grouping to prevent duplicating same
 * values for both [AmperLangTreeReader] and [YamlTreeReader].
 */
internal data class TreeReadRequest(
    val initialType: AmperTypes.Object,
    val initialContexts: Contexts,
    val file: VirtualFile,
    val psiFile: PsiFile,
    val problemCtx: ProblemReporterContext,
    val reportUnknowns: Boolean,
)

// TODO Do we need the read action here?
internal fun TreeReadRequest.readTree(): MapLikeValue<Owned>? =
    ApplicationManager.getApplication().runReadAction(Computable {
        when (psiFile.language) {
//        is AmperLanguage -> AmperLangTreeReader(this).read()
            is YAMLLanguage -> YamlTreeReader(this).read()
                ?: MapLikeValue(emptyList(), psiFile.trace, initialContexts, initialType)

            else -> error("Unsupported language: ${psiFile.language}")
        }
    })

internal class ReaderCtx(params: TreeReadRequest) : ProblemReporterContext by params.problemCtx, PsiReporterCtx {
    companion object {
        private val stringType = String::class.starProjectedType
        private val pathType = Path::class.starProjectedType
    }

    private val baseDir = params.file.parent
    private val contextsStack = Stack<Contexts>().apply { push(params.initialContexts) }
    private val types = Stack<AmperType>().apply { push(params.initialType) }
    private var currentValue: TreeValue<Owned>? = null
    private val currentContexts get() = contextsStack.lastOrNull().orEmpty()

    /**
     * Current type that is being read.
     */
    val currentType: AmperType get() = types.last()

    // Convention constructor functions that are providing [currentContext].
    fun scalarValue(value: Any, trace: Trace) = ScalarValue<Owned>(value, trace, currentContexts)
    fun listValue(children: List<TreeValue<Owned>>, trace: Trace) = ListValue(children, trace, currentContexts)
    fun mapValue(children: List<MapLikeValue.Property<TreeValue<Owned>>>, trace: Trace) =
        MapLikeValue(children, trace, currentContexts, currentType.asSafely<AmperTypes.Object>())

    /**
     * Shortcut to catch the key in the trace also.
     */
    fun PsiElement.parentOrSelfTrace() = parent.asSafely<YAMLKeyValue>()?.asTrace() ?: asTrace()

    /**
     * Execute [block] and change [currentValue] to its return value.
     */
    fun readCurrent(block: ReaderCtx.() -> TreeValue<Owned>?) =
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
     * Try to read value of a specified scalar [kType] from the [YAMLScalar] and return `null` if failed.
     */
    fun tryReadScalar(text: String, kType: KType, origin: PsiElement, report: Boolean = true): Any? {
        fun reportIfNeeded(msgId: String) = if (report) origin.reportAndNull(msgId) else null

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> readAs(newType: KType) = tryReadScalar(text, newType, origin, report) as? T
        return when {
            kType.isString -> text
            kType.isInt -> text.toIntOrNull() ?: reportIfNeeded("validation.expected.integer")
            kType.isBoolean -> text.toBooleanStrictOrNull() ?: reportIfNeeded("validation.expected.boolean")
            kType.isPath -> baseDir.resolveOrNull(text) ?: reportIfNeeded("validation.expected.path")
            kType.isEnum -> tryReadEnum(text, kType, origin, report)
            kType.isTraceableString -> readAs<String>(stringType)?.asTraceable(origin.trace)
            kType.isTraceablePath -> readAs<Path>(pathType)?.asTraceable(origin.trace)
            kType.isTraceableEnum -> readAs<Enum<*>>(kType.traceableType)?.asTraceable(origin.trace)
            else -> error("Unknown type: $kType while reading \"$text\"")
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
     * Try to read value of a specified enum [kType] from the [YAMLScalar] and return `null` if failed.
     */
    private fun tryReadEnum(text: String, kType: KType, origin: PsiElement, report: Boolean): Any? {
        val values = kType.enumValuesOrNull ?: error("Can't get enum values for type: $kType")
        return values.firstOrNull { it.schemaValue == text }
            ?: if (report) origin.reportAndNull(
                problemId = "validation.unknown.enum.value",
                kType.kClass.simpleName?.splitByCamelHumps(),
                text,
                values.joinToString { it.schemaValue },
                messageKey = if (values.size > 10) "validation.unknown.enum.value.short" else "validation.unknown.enum.value",
                level = Level.Error,
            ) else null
    }

    fun <R> withNew(type: AmperType? = null, contexts: Collection<Context> = emptySet(), block: () -> R) =
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