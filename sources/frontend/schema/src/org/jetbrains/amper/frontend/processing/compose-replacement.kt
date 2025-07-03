/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import com.intellij.util.asSafely
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.schema.ExternalMavenDependency
import org.jetbrains.amper.frontend.tree.TreeTransformer
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.Merged
import org.jetbrains.amper.frontend.tree.ScalarValue
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.types.getDeclaration


context(BuildCtx)
internal fun TreeValue<Merged>.substituteComposeOsSpecific() =
    ComposeOsSpecificSubstitutor(this@BuildCtx).visitValue(this)!!

internal class ComposeOsSpecificSubstitutor(
    private val buildCtx: BuildCtx
) : TreeTransformer<Merged>(), ProblemReporterContext by buildCtx {
    private val dependencyType = buildCtx.types.getDeclaration<ExternalMavenDependency>()
    private val coordinatesPName = ExternalMavenDependency::coordinates.name
    private val replacement = "org.jetbrains.compose.desktop:desktop-jvm-${buildCtx.systemInfo.detect().familyArch}:"

    private fun String.doReplace() = this
        .replace("org.jetbrains.compose.desktop:desktop:", replacement)
        .replace("org.jetbrains.compose.desktop:desktop-jvm:", replacement)

    override fun visitMapValue(value: MapLikeValue<Merged>) =
        if (value.type != dependencyType) super.visitMapValue(value)
        else value.copy<ScalarValue<Merged>> { key, pValue, old ->
            pValue.value.asSafely<String>()
                .takeIf { key == coordinatesPName }
                ?.let { old.copy(value = pValue.copy(value = it.doReplace())) }
                .let { listOf(it ?: old) }
        }
}
