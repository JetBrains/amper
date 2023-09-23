package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.helper.AbstractTestWithBuildFile
import org.jetbrains.deft.proto.frontend.helper.assertHasSingleProblem
import org.jetbrains.deft.proto.frontend.helper.testParseWithTemplates
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test

internal class RepositoriesTest : AbstractTestWithBuildFile() {

    @Test
    fun `parsing id and url `() {
        withBuildFile {
            testParseWithTemplates("repositories-id-and-url")
        }
    }

    @Test
    fun `parsing credentials`() {
        withBuildFile {
            testParseWithTemplates("repositories-credentials") {
                copyLocal("repositories-credentials.local.properties")
            }
        }
    }

    @Test
    fun `repositories no credentials file`() {
        withBuildFile {
            testParseWithTemplates("repositories-no-credentials-file", checkErrors = { problems ->
                problems.assertHasSingleProblem {
                    assertTrue("does not exist" in message, message)
                    assertTrue("non.existing.file" in message, message)
                }
            })
        }
    }
}
