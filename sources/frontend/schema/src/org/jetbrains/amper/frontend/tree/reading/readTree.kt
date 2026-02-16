/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.childrenOfType
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.aomBuilder.asPsi
import org.jetbrains.amper.frontend.api.asTrace
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.contexts.Context
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.messages.originalFilePath
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.tree.MappingNode
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLFile
import java.nio.file.Path
import kotlin.io.path.absolute

/**
 * Read a tree from the given [file], using the [type] as the schema.
 *
 * @param reportUnknowns whether to report unknown properties inside object mappings.
 *  If `false`, such properties are silently ignored.
 * @param referenceParsingMode how to treat Amper references (`${...}) syntax in the file. See [ReferencesParsingMode].
 * @param parseContexts whether to treat `@<id>` at the end of the object keys as contexts.
 * @param contexts the contexts of the whole file, e.g., a template context.
 */
@UsedInIdePlugin
context(_: ProblemReporter, _: FrontendPathResolver)
fun readTree(
    file: YAMLFile,
    type: SchemaType.ObjectType,
    vararg contexts: Context,
    reportUnknowns: Boolean = true,
    referenceParsingMode: ReferencesParsingMode = ReferencesParsingMode.IgnoreButWarn,
    parseContexts: Boolean = true,
): MappingNode {
    val rootContexts = contexts.toSet()
    return file.childrenOfType<YAMLDocument>().firstOrNull()?.topLevelValue?.let {
        val config = ParsingConfig(
            basePath = checkNotNull(file.originalFilePath).parent.absolute(),
            skipUnknownProperties = !reportUnknowns,
            supportContexts = parseContexts,
            referenceParsingMode = referenceParsingMode,
        )
        context(config, rootContexts) {
            parseFile(
                file = file,
                type = type,
            )
        }
    } ?: MappingNode(emptyList(), type, file.asTrace(), rootContexts)
}

context(_: ProblemReporter, _: FrontendPathResolver)
internal fun readTree(
    file: VirtualFile,
    declaration: SchemaObjectDeclaration,
    vararg contexts: Context,
    reportUnknowns: Boolean = true,
    referenceParsingMode: ReferencesParsingMode = ReferencesParsingMode.IgnoreButWarn,
    parseContexts: Boolean = true,
): MappingNode {
    val psiFile = file.asPsi()
    return ApplicationManager.getApplication().runReadAction(Computable {
        when (psiFile.language) {
            is YAMLLanguage -> readTree(
                file = psiFile as YAMLFile,
                type = declaration.toType(),
                contexts = contexts,
                reportUnknowns = reportUnknowns,
                referenceParsingMode = referenceParsingMode,
                parseContexts = parseContexts,
            )
            else -> error("Unsupported language: ${psiFile.language}")
        }
    })
}

internal class ParsingConfig(
    val basePath: Path,
    val skipUnknownProperties: Boolean,
    val referenceParsingMode: ReferencesParsingMode,
    val supportContexts: Boolean,
)

context(_: Contexts, _: ParsingConfig, reporter: ProblemReporter)
private fun parseFile(
    file: YAMLFile,
    type: SchemaType.ObjectType,
): MappingNode? {
    val documents = file.childrenOfType<YAMLDocument>()
    if (documents.size > 1) {
        reporter.reportBundleError(
            documents[1].asBuildProblemSource(), "validation.structure.unsupported.multiple.documents"
        )
    }
    val value = documents.first() // Safe - at least one document is always present
        .topLevelValue ?: return null
    return parseNode(YamlValue(value), type) as? MappingNode?
}

enum class ReferencesParsingMode {
    /**
     * Neither parse/nor diagnose references.
     */
    Ignore,

    /**
     * Parse and fully validate references.
     */
    Parse,

    /**
     * We do not parse references as in "yield ReferenceTreeValue", but we diagnose them with warnings.
     * Suited for files where references are not yet supported but planned.
     */
    IgnoreButWarn,
}