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

    @Test
    fun `jvm run`() {
        with(buildFile) {
            testParse("4-jvm-run")
        }
    }

    @Test
    fun `common folder bug`() {
        with(buildFile) {
            testParse("5-common-folder-bug") {
                directory("common") {
                    directory("src")
                }
            }
        }
    }

    @Test
    fun `empty key`() {
        with(buildFile) {
            testParse("6-empty-list-key")
        }
    }

    @Test
    fun `propagating artifact options`() {
        with(buildFile) {
            testParse("7-propagating-artifact-options")
        }
    }

    @Test
    fun `variants even more simplified`() {
        with(buildFile) {
            testParse("8-variants-simplified")
        }
    }
}
