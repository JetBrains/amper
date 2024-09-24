/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.diagnostics

import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.schema.helper.diagnosticsTest
import kotlin.io.path.Path
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.test.Test

class AndroidDiagnosticsTest : TestBase(Path("testResources") / "diagnostics" / "android") {
    @Test
    fun `test Android too old version`() {
        diagnosticsTest("too-old-version")
    }

    @Test
    fun `test Android all versions are at least minSdk`() {
        diagnosticsTest("at-least-min-sdk")
    }

    @Test
    fun `test Android version is at least minSdk from different context`() {
        diagnosticsTest("at-least-min-sdk-from-context")
    }

    @Test
    fun `test keystore properties does not exist`() {
        buildDir.resolve("amper").createFile()
        buildDir.resolve("amper.bat").createFile()
        diagnosticsTest("keystore-properties-does-not-exist", levels = arrayOf(Level.Warning))
    }

    @Test
    fun `test no diagnostics without wrapper`() {
        diagnosticsTest("no-diagnostics-without-wrapper", levels = arrayOf(Level.Warning))
    }
}
