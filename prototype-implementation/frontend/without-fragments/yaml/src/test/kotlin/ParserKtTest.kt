package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.helper.testParse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test

internal class ParserKtTest {

    @TempDir
    lateinit var tempDir: Path

    lateinit var buildFile: Path

    @BeforeEach
    fun setUp() {
        buildFile = tempDir.resolve("build.yaml")
    }

    @Test
    fun `single platform`() {
        with(buildFile) {
            testParse("single-platform")
        }
    }

    @Test
    fun complexTest() {
        with(buildFile) {
            testParse("complex-test")
        }
    }
}
