/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.helper.aomTest
import org.jetbrains.amper.frontend.helper.diagnosticsTest
import org.jetbrains.amper.frontend.old.helper.TestBase
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

internal class VersionCatalogTest : TestBase(Path("testResources") / "catalogs") {

    @Test
    fun `check build in compose catalog versions`() {
        aomTest("build-in-compose-catalogue")
    }

    @Test
    fun `check failure with absent catalog key`() {
        diagnosticsTest("no-catalog-value")
    }

    @Test
    fun `check simple gradle catalog`() {
        aomTest("simple-gradle-version-catalog") {
            amperFiles2gradleCatalogs[amperModuleFiles.first()] =
                listOf(base / "simple-gradle-version-catalog.toml")
        }
    }

    @Test
    fun `check intersecting gradle catalogs`() {
        aomTest("intersecting-gradle-catalogs") {
            amperFiles2gradleCatalogs[amperModuleFiles.first()] =
                listOf(
                    base / "high-priority-version-catalog.toml",
                    base / "low-priority-version-catalog.toml",
                )
        }
    }
}
