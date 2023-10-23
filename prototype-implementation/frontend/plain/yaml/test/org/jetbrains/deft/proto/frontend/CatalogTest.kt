package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.helper.AbstractTestWithBuildFile
import org.jetbrains.deft.proto.frontend.helper.testParse
import kotlin.test.Ignore
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
