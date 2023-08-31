package org.jetbrains.deft.proto.core.messages

import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.io.path.Path
import kotlin.test.Test

class ProblemReporterRenderingTest {
    @Test
    fun `reporting problem without file`() {
        val problem = BuildProblem(message = "Test message", level = Level.Error)
        assertEquals("Test message", renderMessage(problem))
    }

    @Test
    fun `reporting problem with file but no line`() {
        val problem = BuildProblem(file = Path("test.txt"), message = "Test message", level = Level.Error)
        assertEquals("test.txt: Test message", renderMessage(problem))
    }

    @Test
    fun `reporting problem with file and line`() {
        val problem = BuildProblem(file = Path("test.txt"), line = 42, message = "Test message", level = Level.Error)
        assertEquals("test.txt:42: Test message", renderMessage(problem))
    }
}
