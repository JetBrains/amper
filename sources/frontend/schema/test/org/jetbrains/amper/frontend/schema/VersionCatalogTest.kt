/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.old.helper.TestBase
import org.jetbrains.amper.frontend.schema.helper.aomTest
import org.jetbrains.amper.frontend.schema.helper.diagnosticsTest
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
            path2catalog[amperModuleFiles.first()] =
                baseTestResourcesPath / "simple-gradle-version-catalog.toml"
        }
    }
}
