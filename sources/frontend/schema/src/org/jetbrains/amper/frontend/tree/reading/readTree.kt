/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.childrenOfType
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.aomBuilder.asPsi
import org.jetbrains.amper.frontend.api.asTrace
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.contexts.Context
import org.jetbrains.amper.frontend.contexts.Contexts
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
            is YAMLLanguage -> {
                val rootContexts = contexts.toSet()
                psiFile.childrenOfType<YAMLDocument>().firstOrNull()?.topLevelValue?.let {
                    val config = ParsingConfig(
                        basePath = file.parent.toNioPath().absolute(),
                        skipUnknownProperties = !reportUnknowns,
                        supportContexts = parseContexts,
                        referenceParsingMode = referenceParsingMode,
                    )
                    context(config, rootContexts) {
                        parseFile(
                            file = psiFile as YAMLFile,
                            type = declaration.toType(),
                        )
                    }
                } ?: MappingNode(emptyList(), declaration.toType(), psiFile.asTrace(), rootContexts)
            }
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

internal enum class ReferencesParsingMode {
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