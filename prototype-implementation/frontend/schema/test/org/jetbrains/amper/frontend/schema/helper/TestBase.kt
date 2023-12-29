/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.old.helper

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div

abstract class TestBase(
    val baseTestResourcesPath: Path = Path("testResources")
) {
    @TempDir
    lateinit var buildDir: Path

    val buildFile: Path get() = buildDir / "build.yaml"
}
