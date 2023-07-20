package org.jetbrains.deft.proto.frontend

import java.nio.file.Path

sealed interface PotatoModuleSource

object PotatoModuleProgrammaticSource : PotatoModuleSource

data class PotatoModuleFileSource(val buildFile: Path) : PotatoModuleSource {
    val buildDir get() = buildFile.parent
}

enum class PotatoModuleType {
    LIBRARY,
    APPLICATION,
}

sealed interface ModulePart<SelfT> {
    fun default(): ModulePart<SelfT> = error("No default!")
}

data class RepositoriesModulePart(
    val mavenRepositories: List<Repository>
) : ModulePart<RepositoriesModulePart> {
    data class Repository(
        val id: String,
        val url: String,
        val userName: String?,
        val password: String?,
        val publish: Boolean,
    )
}

/**
 * Just an aggregator for fragments and artifacts.
 */
interface PotatoModule {
    /**
     * To reference module somehow in output.
     */
    val userReadableName: String

    val type: PotatoModuleType

    val source: PotatoModuleSource

    val fragments: List<Fragment>

    val artifacts: List<Artifact>

    val parts: ClassBasedSet<ModulePart<*>>
}