package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.helper.testParseWithTemplates
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test

internal class RepositoriesTest {

    @TempDir
    lateinit var tempDir: Path

    private val buildFile get() = tempDir.resolve("build.yaml")

    @Test
    fun `parsing id and url `() {
        with(buildFile) {
            testParseWithTemplates("repositories-id-and-url")
        }
    }

}
