package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.helper.testParse
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test

internal class DependencyFlagsTest {

    @TempDir
    lateinit var tempDir: Path

    private val buildFile get() = object: BuildFileAware {
        override val buildFile: Path
            get() = tempDir.resolve("build.yaml")
    }

    @Test
    fun exported() {
        with(buildFile) {
            testParse("dependency-flags-exported")
        }
    }

    @Test
    fun `compile runtime`() {
        with(buildFile) {
            testParse("dependency-flags-runtime-compile")
        }
    }
}