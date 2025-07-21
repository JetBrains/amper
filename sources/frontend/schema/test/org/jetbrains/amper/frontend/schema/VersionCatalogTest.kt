/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.catalogs.parseGradleVersionCatalog
import org.jetbrains.amper.frontend.schema.helper.aomTest
import org.jetbrains.amper.frontend.schema.helper.diagnosticsTest
import org.jetbrains.amper.test.golden.GoldenTestBase
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test

internal class VersionCatalogTest : GoldenTestBase(Path("testResources") / "catalogs") {

    @Test
    fun `check build in compose catalog versions`() {
        aomTest("built-in-catalogue")
    }

    @Test
    fun `check failure with absent catalog key`() {
        diagnosticsTest("no-catalog-value")
    }

    @Test
    fun `check simple gradle catalog`() {
        aomTest("simple-gradle-version-catalog") {
            val catalogFile = frontendPathResolver.loadVirtualFile(baseTestResourcesPath / "simple-gradle-version-catalog.toml")
            projectVersionsCatalog = frontendPathResolver.parseGradleVersionCatalog(catalogFile)
        }
    }
}
