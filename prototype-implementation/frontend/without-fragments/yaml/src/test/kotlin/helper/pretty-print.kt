package org.jetbrains.deft.proto.frontend.helper

import org.jetbrains.deft.proto.frontend.PotatoModule

internal fun PotatoModule.prettyPrint(): String = buildString {
    append("Module $userReadableName\n")
    append("Fragments:\n")
    for (fragment in fragments) {
        append("  ${fragment.name}\n")
        append("    External dependencies: ${fragment.externalDependencies}\n")
        append("    Fragment dependencies:\n")
        for (dependency in fragment.fragmentDependencies) {
            append("      ${dependency.target.name} (${dependency.type})\n")
        }
        append("    Parts:\n")
        for (part in fragment.parts) {
            append("      ${part::class.simpleName}\n")
        }
    }
    append("Artifacts:\n")
    for (artifact in artifacts) {
        append("  ${artifact.platforms}\n")
        append("    Fragments:\n")
        for (fragment in artifact.fragments) {
            append("      ${fragment.name}\n")
        }
        append("    Parts:\n")
        for (part in artifact.parts) {
            append("      ${part::class.simpleName}\n")
        }
    }
}
