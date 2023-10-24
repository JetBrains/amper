package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.helper.AbstractTestWithBuildFile
import org.jetbrains.amper.frontend.helper.testParse
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
