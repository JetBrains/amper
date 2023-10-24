package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.helper.AbstractTestWithBuildFile
import org.jetbrains.amper.frontend.helper.testParseWithTemplates
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
            testParseWithTemplates("repositories-no-credentials-file")
        }
    }
}
