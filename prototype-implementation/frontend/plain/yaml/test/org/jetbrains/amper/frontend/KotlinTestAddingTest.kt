package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.helper.AbstractTestWithBuildFile
import org.jetbrains.amper.frontend.helper.testParse
import kotlin.test.Test

class KotlinTestAddingTest : AbstractTestWithBuildFile() {
    @Test
    fun `add kotlin-test automatically`() {
        withBuildFile {
            testParse("19-compose-android-without-tests")
        }
    }
}
