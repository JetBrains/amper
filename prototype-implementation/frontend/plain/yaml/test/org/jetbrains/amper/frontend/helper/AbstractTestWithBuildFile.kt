/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.helper

import org.jetbrains.amper.frontend.BuildFileAware
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

abstract class AbstractTestWithBuildFile {
    @TempDir
    lateinit var tempDir: Path

    private val buildFile get() = object: BuildFileAware {
        override val buildFile: Path
            get() = tempDir.resolve("build.yaml")
    }

    protected fun withBuildFile(action: BuildFileAware.() -> Unit) {
        with(buildFile) {
            action()
        }
    }
}
