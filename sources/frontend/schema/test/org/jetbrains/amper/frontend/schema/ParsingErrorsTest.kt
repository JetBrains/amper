/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.helpers.FrontendTestCaseBase
import org.jetbrains.amper.frontend.helpers.diagnosticsTest
import org.jetbrains.amper.frontend.tree.reading.maven.MavenCoordinatesParsingProblem
import org.junit.jupiter.api.fail
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test
import kotlin.test.assertEquals

class ParsingErrorsTest : FrontendTestCaseBase(Path("testResources") / "parsing-errors") {
    @Test
    fun `unexpected value type`() {
        diagnosticsTest("unexpected-value-type/module")
    }

    @Test
    fun `invalid dependencies`() {
        diagnosticsTest("invalid-dependencies/module") { problems ->
            problems.filterIsInstance<MavenCoordinatesParsingProblem>().forEach { problem ->
                val expectedDetailedMessage = when(val coordinates = problem.coordinates) {
                    "com.fasterxml.jackson.core:jackson-core:2.17.2 - ../shared" ->
                        """
                        Maven coordinates should not contain spaces
                        com.fasterxml.jackson.core:jackson-core:2.17.2 - ../shared
                                                                      ^ ^
                    """.trimIndent()

                    "com.fasterxml.     jackson.core:jackson-core:2.17.2" ->
                        """
                        Maven coordinates should not contain spaces
                        com.fasterxml.     jackson.core:jackson-core:2.17.2
                                      ^^^^^
                    """.trimIndent()

                    "com.fasterxml.jackson.core:jackson-core:2.17.2 :exported" ->
                        """
                        Maven coordinates should not contain spaces
                        com.fasterxml.jackson.core:jackson-core:2.17.2 :exported
                                                                      ^
                    """.trimIndent()

                    "com.fasterx/ml.jackson.core:jackson-core:2.17.2" ->
                        """
                        Maven coordinates should not contain slashes
                        com.fasterx/ml.jackson.core:jackson-core:2.17.2
                                   ^
                    """.trimIndent()

                    "com.fasterxml.jackson.core" ->
                        "Maven coordinates $coordinates should contain at least two parts separated by ':', but got 1"

                    "com.fasterxml.jackson.core:jackson-core:jackson-core:jackson-core:jackson-core:2.17.2" ->
                        "Maven coordinates $coordinates should contain at most four parts separated by ':', but got 6"

                    "com.fasterxml.jackson.core:jackson-core:\n2.17.2" ->
                        """
                        Maven coordinates should not contain line breaks
                        com.fasterxml.jackson.core:jackson-core:\n2.17.2
                                                                ^^
                    """.trimIndent()

                    "com.fasterxml.jackson.core:jackson-core.:2.17.2." ->
                        """
                        Maven coordinates should not contain parts ending with dots
                        com.fasterxml.jackson.core:jackson-core.:2.17.2.
                                                   ^^^^^^^^^^^^^ ^^^^^^^
                    """.trimIndent()

                    else -> fail("Unexpected dependency coordinates: $coordinates")
                }
                assertEquals(
                    expected = expectedDetailedMessage,
                    actual = problem.detailedMessage,
                )
            }
        }
    }

    @Test
    fun `unsupported dependency classifiers`() {
        diagnosticsTest("unsupported-dependency-classifiers/module") { problems ->
            problems.filterIsInstance<MavenCoordinatesParsingProblem>().forEach { problem ->
                val expectedError = when (val coordinates = problem.coordinates) {
                    "com.fasterxml.jackson.core:jackson-core:2.17.2:abc" ->
                        """
                        Maven classifiers are currently not supported
                        Dependency com.fasterxml.jackson.core:jackson-core:2.17.2:abc has classifier 'abc', which will be ignored.
                    """.trimIndent()

                    "com.fasterxml.jackson.core:jackson-core:2.17.2:compile-only" ->
                        """
                        Maven classifiers are currently not supported
                        Dependency com.fasterxml.jackson.core:jackson-core:2.17.2:compile-only has classifier 'compile-only', which will be ignored. Perhaps, you have meant 'compile-only' as a scope? Add a space after the last ':' to fix that.
                    """.trimIndent()

                    "com.fasterxml.jackson.core:jackson-core:2.17.2:exported" ->
                        """
                        Maven classifiers are currently not supported
                        Dependency com.fasterxml.jackson.core:jackson-core:2.17.2:exported has classifier 'exported', which will be ignored. Perhaps, you have meant 'exported' as a scope? Add a space after the last ':' to fix that.
                    """.trimIndent()

                    "com.fasterxml.jackson.core:jackson-core:2.17.2:runtime-only" ->
                        """
                        Maven classifiers are currently not supported
                        Dependency com.fasterxml.jackson.core:jackson-core:2.17.2:runtime-only has classifier 'runtime-only', which will be ignored. Perhaps, you have meant 'runtime-only' as a scope? Add a space after the last ':' to fix that.
                    """.trimIndent()

                    else -> fail("Unexpected dependency coordinates: $coordinates")
                }
                assertEquals(expected = expectedError, actual = problem.detailedMessage)
            }
        }
    }

    @Test
    fun `unsupported constructs`() {
        diagnosticsTest("unsupported-constructs/module")
    }

    @Test
    fun `unsupported references`() {
        diagnosticsTest("unsupported-references/module")
    }
}