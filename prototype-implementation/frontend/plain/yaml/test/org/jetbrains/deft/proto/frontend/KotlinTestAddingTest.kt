package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.helper.testParse
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test

class KotlinTestAddingTest {
    @TempDir
    lateinit var tempDir: Path

    private val buildFile get() = object: BuildFileAware {
        override val buildFile: Path
            get() = tempDir.resolve("build.yaml")
    }

    @Test
    fun `add kotlin-test automatically`() {
        with(buildFile) {
            testParse("19-compose-android-without-tests")
        }
    }
}