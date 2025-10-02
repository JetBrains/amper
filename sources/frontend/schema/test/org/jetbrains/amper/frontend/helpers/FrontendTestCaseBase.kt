/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.helpers

import org.jetbrains.amper.test.Dirs
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

abstract class FrontendTestCaseBase(
    
    /**
     * Common base path to the golden files' location.
     */
    val base: Path,
) {
    /**
     * The path that is treated as a root of the test project by
     * being passed to the [TestProjectContext.projectRootDir].
     */
    lateinit var buildDir: Path

    fun copyLocalToBuild(localName: String) {
        val localFile = base.resolve(localName).normalize().takeIf(Path::exists)
        val newPath = buildDir.resolve(localName)
        localFile?.copyTo(newPath, overwrite = true)
    }
    
    @BeforeEach
    fun beforeEach() {
        buildDir = createTempDirectory(Dirs.tempDir, "test-base")
    }

    @AfterEach
    fun afterEach() {
        buildDir.deleteRecursively()
    }
}