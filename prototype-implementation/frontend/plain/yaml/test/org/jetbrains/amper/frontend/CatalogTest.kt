package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.helper.AbstractTestWithBuildFile
import org.jetbrains.amper.frontend.helper.testParse
import kotlin.test.Test

internal class CatalogTest : AbstractTestWithBuildFile() {

    @Test
    fun `check build in compose catalog versions`() = withBuildFile {
        testParse("build-in-compose-catalogue")
    }

    @Test
    fun `check failure with absent catalog key`() = withBuildFile {
        testParse("no-catalog-value")
    }
}
