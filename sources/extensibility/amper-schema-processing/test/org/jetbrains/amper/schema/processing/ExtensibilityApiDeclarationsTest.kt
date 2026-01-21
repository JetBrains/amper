/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.plugins.schema.model.PluginDeclarationsRequest
import org.jetbrains.amper.plugins.schema.model.withoutOrigin
import org.jetbrains.amper.test.assertEqualsWithDiff
import org.jetbrains.amper.test.normalizeLineSeparators
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalSerializationApi::class)
class ExtensibilityApiDeclarationsTest {
    private val extensibilityApiSrc = Path("../amper-extensibility-api/src").absolute().normalize()
    private val request = PluginDeclarationsRequest.Request(
        moduleName = "amper-extensibility-api",
        sourceDir = extensibilityApiSrc,
        isParsingAmperApi = true,
    )

    private val prettyJson = Json { prettyPrint = true }

    @Test
    fun `are builtin declarations up-to-date`() {
        // Loads the bundled declarations json from the `amper-schema-processing` dependency
        val expected = loadSerializedBuiltinDeclarations().normalizeLineSeparators()

        val result = runSchemaProcessor(PluginDeclarationsRequest(listOf(request))).single()
        assertEquals(
            expected = emptyList(),
            actual = result.diagnostics,
        )

        val actual = prettyJson.encodeToString<PluginData.Declarations>(result.declarations.withoutOrigin())

        val tmpFile = Path("resources/META-INF/amper/extensibility-api-declarations.json.tmp")
        if (actual != expected) {
            tmpFile.writeText(actual)
        } else {
            tmpFile.deleteIfExists()
        }

        assertEqualsWithDiff(
            expected = expected,
            actual = actual,
            message = MESSAGE,
        )
    }

    @Test
    fun `are shadow sources up-to-date`() {
        val sourceFile = Path(SHADOW_SOURCE_FILE)
            .absolute().normalize()
        val actual = sourceFile.readText().normalizeLineSeparators()

        val result = runSchemaProcessor(PluginDeclarationsRequest(listOf(request))).single()
        val expected = generateShadowSources(result.declarations).toString()

        val tmpFile = sourceFile.parent / (sourceFile.name + ".tmp")
        if (expected != actual) {
            tmpFile.writeText(expected)
        } else {
            tmpFile.deleteIfExists()
        }
        assertEqualsWithDiff(
            expected = expected,
            actual = actual,
            message = MESSAGE,
        )
    }
}

private const val SHADOW_SOURCE_FILE =
    "../../frontend-api/src/org/jetbrains/amper/frontend/plugins/generated/generatedShadowSchema.kt"

private const val MESSAGE = "If you have made changes to the `amper-extensibility-api` code, " +
        "please update the `extensibility-api-declarations.json` file in `amper-schema-processing` lib " +
        "and update the file \"$SHADOW_SOURCE_FILE\""