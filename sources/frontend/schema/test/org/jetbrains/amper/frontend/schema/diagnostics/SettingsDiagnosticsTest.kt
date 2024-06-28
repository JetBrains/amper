/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.diagnostics

import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.schema.helper.diagnosticsTest
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

class SettingsDiagnosticsTest : TestBase(Path("testResources") / "diagnostics") {
    @Test
    fun `compose version when compose disabled`() {
        diagnosticsTest("compose-version-disabled", levels = arrayOf(Level.Warning))
    }

    @Test
    fun `setting has default value`() {
        diagnosticsTest("setting-has-default-value", levels = arrayOf(Level.Redundancy))
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
}