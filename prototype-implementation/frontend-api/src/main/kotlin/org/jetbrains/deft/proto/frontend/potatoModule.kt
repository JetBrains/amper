package org.jetbrains.deft.proto.frontend

import java.nio.file.Path

sealed interface PotatoModuleSource

object PotatoModuleProgrammaticSource : PotatoModuleSource

data class PotatoModuleFileSource(val buildFile: Path) : PotatoModuleSource

enum class PotatoModuleType {
    LIBRARY,
    APPLICATION,
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
}