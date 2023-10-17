package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.helper.AbstractTestWithBuildFile
import org.jetbrains.deft.proto.frontend.helper.testParse
import kotlin.test.Test

internal class CatalogueTest : AbstractTestWithBuildFile() {

    @Test
    fun `check build in compose catalogue versions`() = withBuildFile {
        testParse("build-in-compose-catalogue")
    }

    @Test
    fun `check failure with absent catalogue key`() = withBuildFile {
        testParse("no-catalogue-value")
    }
}
