package org.jetbrains.deft.proto.frontend

import java.net.URI
import java.nio.file.Path

sealed interface PotatoModuleSource

object PotatoModuleProgrammaticSource : PotatoModuleSource

data class PotatoModuleFileSource(val buildFile: Path) : PotatoModuleSource

enum class PotatoModuleType {
    LIBRARY,
    APPLICATION,
}

sealed interface ModulePart<SelfT> {
    fun default(): ModulePart<SelfT> = error("No default!")
}

data class PublicationModulePart(
    val mavenRepositories: List<Repository>
) : ModulePart<PublicationModulePart> {
    data class Repository(
            val name: String,
            val url: URI,
            val userName: String,
            val password: String,
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