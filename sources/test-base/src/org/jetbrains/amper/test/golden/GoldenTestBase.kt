/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test.golden

import org.jetbrains.amper.test.Dirs
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively

interface GoldenTest {
    fun buildDir(): Path
    fun baseTestResourcesPath(): Path = Path("testResources")
}

abstract class GoldenTestBase(
    open val baseTestResourcesPath: Path = Path("testResources")
): GoldenTest {
    // todo (AB) : Why is it needed at all?
    lateinit var buildDir: Path

    @BeforeEach
    fun beforeEach() {
        buildDir = createTempDirectory(Dirs.tempDir, "test-base")
    }

    @AfterEach
    fun afterEach() {
        buildDir.deleteRecursively()
    }

    override fun buildDir(): Path = buildDir
    override fun baseTestResourcesPath(): Path = baseTestResourcesPath
}
