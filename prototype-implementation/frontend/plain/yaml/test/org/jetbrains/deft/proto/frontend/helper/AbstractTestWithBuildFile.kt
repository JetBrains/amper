package org.jetbrains.deft.proto.frontend.helper

import org.jetbrains.deft.proto.frontend.BuildFileAware
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

abstract class AbstractTestWithBuildFile {
    @TempDir
    lateinit var tempDir: Path

    private val buildFile get() = object: BuildFileAware {
        override val buildFile: Path
            get() = tempDir.resolve("build.yaml")
    }

    protected fun withBuildFile(action: BuildFileAware.() -> Unit) {
        with(buildFile) {
            action()
        }
    }
}
