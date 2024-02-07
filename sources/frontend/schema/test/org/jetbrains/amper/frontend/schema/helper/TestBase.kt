/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.old.helper

import org.jetbrains.amper.test.TestUtil
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div

abstract class TestBase(
    val baseTestResourcesPath: Path = Path("testResources")
) {
    lateinit var buildDir: Path

    @BeforeEach
    fun beforeEach() {
        buildDir = Files.createTempDirectory(TestUtil.tempDir, "test-base")
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterEach
    fun afterEach() {
        buildDir.deleteRecursively()
    }

    val buildFile: Path get() = buildDir / "build.yaml"
}
