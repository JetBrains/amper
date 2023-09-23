package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.helper.AbstractTestWithBuildFile
import org.jetbrains.deft.proto.frontend.helper.testParse
import kotlin.test.Test

internal class DependencyFlagsTest : AbstractTestWithBuildFile() {

    @Test
    fun exported() {
        withBuildFile {
            testParse("dependency-flags-exported")
        }
    }

    @Test
    fun `compile runtime`() {
        withBuildFile {
            testParse("dependency-flags-runtime-compile")
        }
    }
}
