package org.jetbrains.deft.proto.frontend.helper

import org.jetbrains.deft.proto.frontend.PotatoModule

internal fun PotatoModule.prettyPrint(): String = buildString {
    appendLine("Module $userReadableName")
    appendLine("Fragments:")
    for (fragment in fragments.sortedBy { it.name }) {
        appendLine("  ${fragment.name}")
        appendLine("    External dependencies:")
        for (dependency in fragment.externalDependencies) {
            appendLine("      $dependency")
        }
        append("    Src folder: ${fragment.src?.fileName}")
        appendLine("    Fragment dependencies:")
        for (dependency in fragment.fragmentDependencies) {
            appendLine("      ${dependency.target.name} (${dependency.type})")
        }
        appendLine("    Parts:")
        for (part in fragment.parts) {
            appendLine("      ${part.value}")
        }
    }
    appendLine("Artifacts:")
    for (artifact in artifacts.sortedBy { it.name }) {
        appendLine("  isTest: ${artifact.isTest}")
        appendLine("  ${artifact.platforms}")
        appendLine("    Fragments:")
        for (fragment in artifact.fragments) {
            appendLine("      ${fragment.name}")
        }
        appendLine("    Parts:")
        for (part in artifact.parts) {
            appendLine("      ${part.value}")
        }
    }
}
