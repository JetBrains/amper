/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.childrenOfType
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.api.asTrace
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.contexts.Context
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.Owned
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.toType
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLFile
import java.nio.file.Path
import kotlin.io.path.absolute

internal fun BuildCtx.readTree(
    file: VirtualFile,
    declaration: SchemaObjectDeclaration,
    vararg contexts: Context,
    reportUnknowns: Boolean = true,
    parseReferences: Boolean = false,
    parseContexts: Boolean = true,
): MapLikeValue<*> {
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
                        supportReferences = parseReferences,
                    )
                    context(config, problemReporter, rootContexts) {
                        parseFile(
                            file = psiFile as YAMLFile,
                            type = declaration.toType(),
                        )
                    }
                } ?: Owned(emptyList(), declaration.toType(), psiFile.asTrace(), rootContexts)
            }
            else -> error("Unsupported language: ${psiFile.language}")
        }
    })
}

internal class ParsingConfig(
    val basePath: Path,
    val skipUnknownProperties: Boolean = false,
    val supportReferences: Boolean = false,
    val supportContexts: Boolean = true,
)

context(_: Contexts, _: ParsingConfig, reporter: ProblemReporter)
private fun parseFile(
    file: YAMLFile,
    type: SchemaType.ObjectType,
): Owned? {
    val documents = file.childrenOfType<YAMLDocument>()
    if (documents.size > 1) {
        reporter.reportBundleError(
            documents[1].asBuildProblemSource(), "validation.structure.unsupported.multiple.documents"
        )
    }
    val value = documents.first() // Safe - at least one document is always present
        .topLevelValue ?: return null
    return parseValue(value, type) as? Owned?
}