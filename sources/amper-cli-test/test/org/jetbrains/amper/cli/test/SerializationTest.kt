/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import io.opentelemetry.api.common.AttributeKey
import org.jetbrains.amper.cli.test.utils.assertLogStartsWith
import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.readTelemetrySpans
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.test.spans.assertHasAttribute
import org.jetbrains.amper.test.spans.spansNamed
import org.jetbrains.amper.test.spans.withAttribute
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.slf4j.event.Level

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class SerializationTest: AmperCliTestBase() {

    @Test
    fun `jvm kotlin serialization support without explicit dependency`() = runSlowTest {
        val result = runCli(testProject("kotlin-serialization-default"), "run")

        result.assertLogStartsWith("Process exited with exit code 0", Level.INFO)
        result.assertStdoutContains("Hello, World!")
    }

    @Test
    fun `jvm kotlin serialization support with custom version`() = runSlowTest {
        val result = runCli(testProject("kotlin-serialization-custom-version"), "build")

        result.readTelemetrySpans()
            .spansNamed("resolve-dependencies")
            .withAttribute(AttributeKey.booleanKey("isTest"), false)
            .assertSingle()
            .assertHasAttribute(
                key = AttributeKey.stringArrayKey("dependencies"),
                value = listOf(
                    "org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}",
                    "org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.5.1",
                    "org.jetbrains.kotlinx:kotlinx-serialization-core:1.5.1",
                    "org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1",
                ),
            )
    }
}
