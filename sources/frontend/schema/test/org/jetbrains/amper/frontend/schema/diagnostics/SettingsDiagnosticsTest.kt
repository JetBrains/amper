/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.diagnostics

import org.jetbrains.amper.frontend.schema.helper.diagnosticsTest
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.test.golden.GoldenTestBase
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

class SettingsDiagnosticsTest : GoldenTestBase(Path("testResources") / "diagnostics") {
    @Test
    fun `compose version when compose disabled`() {
        diagnosticsTest("compose-version-disabled", levels = arrayOf(Level.Warning))
    }

    @Test
    fun `serialization version when serialization disabled`() {
        diagnosticsTest("serialization-version-disabled", levels = arrayOf(Level.Warning))
    }

    @Test
    fun `no redundancy for kotlin serialization format`() {
        diagnosticsTest("setting-no-redundancy-kotlin-serialization", levels = arrayOf(Level.Redundancy))
    }

    @Test
    fun `setting overrides same value`() {
        diagnosticsTest("setting-overrides-same-value", levels = arrayOf(Level.Redundancy))
    }

    @Test
    fun `ios setting jvm app`() {
        diagnosticsTest("ios-setting-jvm-app", levels = arrayOf(Level.Warning))
    }

    @Test
    fun `android settings jvm ios lib`() {
        diagnosticsTest("android-settings-jvm-ios-lib", levels = arrayOf(Level.Warning))
    }

    @Test
    fun `setting main class with lib`() {
        diagnosticsTest("setting-main-class-with-lib", levels = arrayOf(Level.Warning))
    }

    @Test
    fun `settings context specificity`() {
        diagnosticsTest("settings-context-specificity", levels = arrayOf(Level.Warning))
    }

    @Test
    fun `settings no modifiers allowed`() {
        diagnosticsTest("settings-no-modifiers-allowed", levels = arrayOf(Level.Error))
    }

    @Test
    fun `settings context specificity valid with aliases`() {
        diagnosticsTest("settings-context-specificity-valid-with-aliases", levels = arrayOf(Level.Warning))
    }
}
