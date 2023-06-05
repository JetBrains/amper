package org.jetbrains.deft.proto.frontend.model

import org.jetbrains.deft.proto.frontend.*
import java.nio.file.Path

class DumbGradleModule(private val file: Path) : PotatoModule {
    override val userReadableName: String
        get() = file.parent.fileName.toString()
    override val type: PotatoModuleType
        get() = PotatoModuleType.LIBRARY
    override val source: PotatoModuleSource
        get() = PotatoModuleFileSource(file)
    override val fragments: List<Fragment>
        get() = listOf()
    override val artifacts: List<Artifact>
        get() = listOf()

    override val parts =
        classBasedSet<ModulePart<*>>()
}