package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.helper.assertHasSingleProblem
import org.jetbrains.deft.proto.frontend.helper.testParseWithTemplates
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test

internal class RepositoriesTest {

    @TempDir
    lateinit var tempDir: Path

    private val buildFile
        get() = object : BuildFileAware {
            override val buildFile: Path
                get() = tempDir.resolve("build.yaml")
        }

    @Test
    fun `parsing id and url `() {
        with(buildFile) {
            testParseWithTemplates("repositories-id-and-url")
        }
    }

    @Test
    fun `parsing credentials`() {
        with(buildFile) {
            testParseWithTemplates("repositories-credentials") {
                copyLocal("repositories-credentials.local.properties")
            }
        }
    }

    @Test
    fun `repositories no credentials file`() {
        with(buildFile) {
            testParseWithTemplates("repositories-no-credentials-file", checkErrors = { problems ->
                problems.assertHasSingleProblem {
                    assertTrue("does not exist" in message, message)
                    assertTrue("non.existing.file" in message, message)
                }
            })
        }
    }
}
