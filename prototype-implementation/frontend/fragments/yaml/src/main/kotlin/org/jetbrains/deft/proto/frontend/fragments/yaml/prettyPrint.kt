package org.jetbrains.deft.proto.frontend.fragments.yaml

import org.jetbrains.deft.proto.frontend.PotatoModule
import org.jetbrains.deft.proto.frontend.PotatoModuleFileSource
import org.jetbrains.deft.proto.frontend.PotatoModuleProgrammaticSource

internal fun prettyPrint(potato: PotatoModule): String {
    check(potato is PotatoModuleImpl) { "Pretty print is supported only for PotatoModuleImpl" }

    return buildString {
        appendLine("Potato ${potato.userReadableName}")
        appendLine("Type ${potato.type}")
        when (val source = potato.source) {
            is PotatoModuleFileSource -> appendLine("Created from ${source.buildFile}")
            PotatoModuleProgrammaticSource -> appendLine("Created programmatically")
        }
        appendLine("Fragments:")
        for (fragment in potato.fragments) {
            appendLine("- ${fragment.name}")
            appendLine("  Fragment dependencies: [${fragment.fragmentDependencies.joinToString { "${it.target.name} [${it.type}]" }}]")
            appendLine("  External dependencies: ${fragment.externalDependencies}")
            if (fragment.parts.isNotEmpty()) {
                appendLine("  Parts: ${fragment.parts}")
            }
            appendLine("  Platforms: ${fragment.platforms}")
        }

        appendLine("Artifacts:")
        for (artifact in potato.artifacts) {
            appendLine("- Built from [${artifact.fragments.joinToString { it.name }}]")
            appendLine("  For platforms ${artifact.platforms}")
            if (artifact.parts.isNotEmpty()) {
                appendLine("  Parts: ${artifact.parts}")
            }
        }
    }
}
