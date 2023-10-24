package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.helper.AbstractTestWithBuildFile
import org.jetbrains.amper.frontend.helper.testParseWithTemplates
import kotlin.test.Test

internal class TemplatesTest : AbstractTestWithBuildFile() {
    
    @Test
    fun `check artifacts of multi-variant builds`() {
        withBuildFile {
            testParseWithTemplates("templates-simple")
        }
    }

    @Test
    fun `check path literals are adjusted`() {
        withBuildFile {
            testParseWithTemplates("templates-adjust-path-test")
        }
    }

    @Test
    fun `empty template file`() {
        withBuildFile {
            testParseWithTemplates("templates-empty-file")
        }
    }
    
    @Test
    fun `empty apply list file`() {
        withBuildFile {
            testParseWithTemplates("templates-empty-apply")
        }
    }
}
