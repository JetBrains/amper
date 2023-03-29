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
            testParse("0-single-platform")
        }
    }

    @Test
    fun `multiplatform app`() {
        with(buildFile) {
            testParse("1-multiplatform-app")
        }
    }

    @Test
    fun aliases() {
        with(buildFile) {
            testParse("2-aliases") {
                directory("iosSimulator")
            }
        }
    }

    @Test
    fun variants() {
        with(buildFile) {
            testParse("3-variants")
        }
    }
}
