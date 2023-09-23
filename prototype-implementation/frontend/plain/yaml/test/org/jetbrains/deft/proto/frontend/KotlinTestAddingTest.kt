package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.helper.AbstractTestWithBuildFile
import org.jetbrains.deft.proto.frontend.helper.testParse
import kotlin.test.Test

class KotlinTestAddingTest : AbstractTestWithBuildFile() {
    @Test
    fun `add kotlin-test automatically`() {
        withBuildFile {
            testParse("19-compose-android-without-tests")
        }
    }
}
