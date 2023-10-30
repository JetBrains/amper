/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import java.nio.file.Path

sealed interface PotatoModuleSource

object PotatoModuleProgrammaticSource : PotatoModuleSource

data class PotatoModuleFileSource(val buildFile: Path) : PotatoModuleSource {
    val buildDir get() = buildFile.parent
}

sealed interface ModulePart<SelfT>

data class RepositoriesModulePart(
    val mavenRepositories: List<Repository>
) : ModulePart<RepositoriesModulePart> {
    data class Repository(
        val id: String,
        val url: String,
        val publish: Boolean = false,
        val userName: String? = null,
        val password: String? = null,
    )
}

enum class Layout {
    /**
     * Mode, when Gradle kotlin source sets layout is preserved.
     * `commonMain` directory is renamed to `common`.
     */
    GRADLE,

    /**
     * Mode, like [GRADLE], except that `jvm` source set is
     * renamed by `main` to be compatible with kotlin("jvm")
     */
    GRADLE_JVM,

    /**
     * Mode, when `src` and `src@jvm` like platform
     * specific directories layout are used.
     * Non-deft source sets have no directories at all.
     */
    DEFT,
}

data class MetaModulePart(
    val layout: Layout = Layout.DEFT
) : ModulePart<MetaModulePart>

/**
 * Just an aggregator for fragments and artifacts.
 */
interface PotatoModule {
    /**
     * To reference module somehow in output.
     */
    val userReadableName: String

    val type: ProductType

    val source: PotatoModuleSource

    val fragments: List<Fragment>

    val artifacts: List<Artifact>

    val parts: ClassBasedSet<ModulePart<*>>
}