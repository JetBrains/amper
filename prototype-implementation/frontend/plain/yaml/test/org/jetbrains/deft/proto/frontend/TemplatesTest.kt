package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.helper.testParseWithTemplates
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test

internal class TemplatesTest {

    @TempDir
    lateinit var tempDir: Path

    private val buildFile get() = object: BuildFileAware {
        override val buildFile: Path
            get() = tempDir.resolve("build.yaml")
    }

    @Test
    fun `check artifacts of multi-variant builds`() {
        with(buildFile) {
            testParseWithTemplates("templates-simple")
        }
    }

    @Test
    fun `check path literals are adjusted`() {
        with(buildFile) {
            testParseWithTemplates("templates-adjust-path-test")
        }
    }

    @Test
    fun `empty template file`() {
        with(buildFile) {
            testParseWithTemplates("templates-empty-file")
        }
    }
    
    @Test
    fun `empty apply list file`() {
        with(buildFile) {
            testParseWithTemplates("templates-empty-apply")
        }
    }
}
