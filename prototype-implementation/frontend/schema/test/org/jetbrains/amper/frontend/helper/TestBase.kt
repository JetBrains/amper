/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.old.helper

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div

data class BuildFileAware(
    val buildDir: Path,
    val buildFile: Path = buildDir / "build.yaml",
)

abstract class TestBase(
    val base: Path = Path("testResources")
) {
    @TempDir
    lateinit var tempDir: Path

    val buildFile
        get() = BuildFileAware(tempDir)

    protected fun withBuildFile(action: BuildFileAware.() -> Unit) =
        with(buildFile, action)
}
