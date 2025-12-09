/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.tree.Changed
import org.jetbrains.amper.frontend.tree.MappingNode
import org.jetbrains.amper.frontend.tree.NotChanged
import org.jetbrains.amper.frontend.tree.ScalarNode
import org.jetbrains.amper.frontend.tree.TreeTransformer
import org.jetbrains.amper.frontend.tree.copyWithValue
import org.jetbrains.amper.frontend.types.SchemaType

context(buildCtx: BuildCtx)
internal fun MappingNode.substituteComposeOsSpecific() =
    ComposeOsSpecificSubstitutor(buildCtx).transform(this) as? MappingNode ?: this

internal class ComposeOsSpecificSubstitutor(buildCtx: BuildCtx) : TreeTransformer() {
    private val replacement = "org.jetbrains.compose.desktop:desktop-jvm-${buildCtx.systemInfo.familyArch}:"

    private fun String.doReplace() = this
        .replace("org.jetbrains.compose.desktop:desktop:", replacement)
        .replace("org.jetbrains.compose.desktop:desktop-jvm:", replacement)

    override fun visitScalar(node: ScalarNode) = when (val type = node.type) {
        is SchemaType.StringType if type.semantics == SchemaType.StringType.Semantics.MavenCoordinates ->
            Changed(node.copyWithValue(value = (node.value as String).doReplace()))
        else -> NotChanged
    }
}
