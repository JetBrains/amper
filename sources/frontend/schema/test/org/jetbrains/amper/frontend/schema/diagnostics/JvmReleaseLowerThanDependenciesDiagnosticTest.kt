/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.diagnostics

import org.jetbrains.amper.frontend.helpers.FrontendTestCaseBase
import org.jetbrains.amper.frontend.helpers.diagnosticsTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.Path
import kotlin.io.path.div

class JvmReleaseLowerThanDependenciesDiagnosticTest : FrontendTestCaseBase(Path("testResources") / "diagnostics" / "jvm-release-mismatch") {

    @ParameterizedTest
    @MethodSource("allModulesInTestProject")
    fun `jvm release lower than dependencies`(moduleName: String) {
        diagnosticsTest(
            caseName = "$moduleName/module",
            additionalFiles = (allModulesInTestProject() - moduleName).map { "$it/module.yaml" },
        )
    }

    companion object {
        @JvmStatic
        fun allModulesInTestProject(): List<String> = listOf(
            "android-app",
            "ios-app",
            "lib-a",
            "lib-b",
            "lib-c",
            "linux-app",
            "macos-app",
            "windows-app",
        )
    }
}
