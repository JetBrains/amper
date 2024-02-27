/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core.messages

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test

@OptIn(NonIdealDiagnostic::class)
class ProblemReporterRenderingTest {
    @Test
    fun `reporting problem without file`() {
        val problem = BuildProblem(
            buildProblemId = "test.message",
            source = GlobalBuildProblemSource,
            message = "Test message",
            level = Level.Error,
        )
        assertEquals("Test message", renderMessage(problem))
    }

    @Test
    fun `reporting problem with file but no line`() {
        val problem = BuildProblem(
            buildProblemId = "test.message",
            source = TestFileProblemSource(Path("test.txt")),
            message = "Test message",
            level = Level.Error
        )
        assertEquals("test.txt: Test message", renderMessage(problem))
    }

    @Test
    fun `reporting problem with file and line`() {
        val problem = BuildProblem(
            buildProblemId = "test.message",
            source = TestFileWithRangesProblemSource(
                Path("test.txt"), range = LineAndColumnRange(
                    LineAndColumn(10, 15, null), LineAndColumn.NONE
                )
            ),
            message = "Test message",
            level = Level.Error
        )
        assertEquals("test.txt:10:15: Test message", renderMessage(problem))
    }

    @Test
    fun `reporting problem with multiple locations`() {
        val location1 = TestFileWithRangesProblemSource(
            Path("test.txt"), range = LineAndColumnRange(
                LineAndColumn(10, 15, null), LineAndColumn.NONE
            )
        )
        val location2 = TestFileWithRangesProblemSource(
            Path("test2.txt"), range = LineAndColumnRange(
                LineAndColumn(9, 15, null), LineAndColumn.NONE
            )
        )
        val location3 = TestFileWithRangesProblemSource(
            Path("test2.txt"), range = LineAndColumnRange(
                LineAndColumn(10, 15, null), LineAndColumn.NONE
            )
        )
        val problem = BuildProblem(
            buildProblemId = "test.message",
            source = MultipleLocationsBuildProblemSource(location1, location2, location3),
            message = "Test message",
            level = Level.Error
        )
        assertEquals(
            """
            test.txt:10:15: Test message
            test2.txt:9:15: Test message
            test2.txt:10:15: Test message
            """.trimIndent(), renderMessage(problem)
        )
    }

    @Test
    fun `nested structures for build problem sources are forbidden`() {
        assertThrows<IllegalArgumentException> {
            MultipleLocationsBuildProblemSource(MultipleLocationsBuildProblemSource(GlobalBuildProblemSource, GlobalBuildProblemSource), GlobalBuildProblemSource)
        }
    }

    private data class TestFileProblemSource(override val file: Path) : FileBuildProblemSource

    private data class TestFileWithRangesProblemSource(
        override val file: Path,
        override val range: LineAndColumnRange,
    ) : FileWithRangesBuildProblemSource {
        override val offsetRange: IntRange = IntRange.EMPTY
    }
}
