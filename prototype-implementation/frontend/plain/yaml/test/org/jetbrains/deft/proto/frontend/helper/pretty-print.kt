package org.jetbrains.deft.proto.frontend.helper

import org.jetbrains.deft.proto.frontend.PotatoModule
import org.jetbrains.deft.proto.frontend.RepositoriesModulePart

internal fun PotatoModule.prettyPrint(): String {
    return buildString {
        appendLine("Module $userReadableName")
        appendLine("Fragments:")
        for (fragment in fragments.sortedBy { it.name }) {
            appendLine("  ${fragment.name}")
            appendLine("    External dependencies:")
            for (dependency in fragment.externalDependencies) {
                appendLine("      $dependency")
            }
            appendLine("    Src folder: ${fragment.src?.fileName}")
            appendLine("    Fragment dependencies:")
            for (dependency in fragment.fragmentDependencies) {
                appendLine("      ${dependency.target.name} (${dependency.type})")
            }
            appendLine("    Parts:")
            for (part in fragment.parts) {
                appendLine("      $part")
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
        }

        val repositories = parts[RepositoriesModulePart::class.java]?.mavenRepositories
        if (!repositories.isNullOrEmpty()) {
            appendLine("Repositories:")
            repositories.forEach {
                appendLine("  - id: ${it.id}")
                appendLine("    url: ${it.url}")
            }
        }
    }
}
