/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.intellij.lang.annotations.Language
import org.jetbrains.amper.plugins.schema.model.PluginDataResponse
import org.jetbrains.amper.plugins.schema.model.PluginDeclarationsRequest
import org.jetbrains.amper.plugins.schema.model.withoutOrigin
import org.jetbrains.amper.test.TempDirExtension
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals

abstract class SchemaProcessorTestBase {
    @RegisterExtension
    private val tempDirExtension = TempDirExtension()

    interface TestBuilder {
        fun givenSourceFile(
            @Language("kotlin") contents: String,
            packageName: String = "com.example",
            name: String = "plugin.kt",
        )

        fun expectPluginData(@Language("json") result: String)

        fun givenSchemaExtensionClassName(name: String)
    }

    protected fun runTest(
        block: TestBuilder.() -> Unit,
    ) {
        class Source(
            contents: String,
            packageName: String,
            val name: String,
        ) {
            val contents = """
                package $packageName
                import org.jetbrains.amper.plugins.*
                import java.nio.file.Path
                                
            """.trimIndent() + contents
            val contentsWithoutComments = this.contents.replace(MultiLineCommentRegex, "")
            val path = tempDirExtension.path.resolve(name)
        }

        val sources = mutableListOf<Source>()
        var schemaExtensionClassName: String? = null
        var expectedJsonPluginData: String? = null
        val builder = object : TestBuilder {
            override fun givenSourceFile(contents: String, packageName: String, name: String) {
                sources += Source(contents, packageName, name)
            }

            override fun expectPluginData(result: String) {
                expectedJsonPluginData = result
            }

            override fun givenSchemaExtensionClassName(name: String) {
                schemaExtensionClassName = name
            }
        }
        builder.block()

        for (source in sources) {
            check(source.name.endsWith(".kt")) { "Kotlin source expected" }
            source.path.writeText(source.contentsWithoutComments)
        }

        val request = PluginDeclarationsRequest.Request(
            moduleName = "test-plugin",
            moduleExtensionSchemaName = schemaExtensionClassName,
            sourceDir = tempDirExtension.path,
        )

        val result = runSchemaProcessor(PluginDeclarationsRequest(listOf(request))).single()
        val groupedDiagnostics: Map<Path, List<PluginDataResponse.Diagnostic>> =
            result.diagnostics.groupBy { it.location.path }
        for (source in sources) {
            val relevantErrors = groupedDiagnostics[source.path].orEmpty()
            val markers = mutableListOf<Pair<String, Int>>()
            for (error in relevantErrors) {
                markers += "/*{{*/" to error.location.textRange.first
                markers += "/*}} ${error.message} */" to error.location.textRange.last
            }
            // Sorting all the markers to insert them one-by-one from the end to avoid offsets recalculation
            markers.sortByDescending { (_, position) -> position }

            val markedContents = StringBuilder(source.contentsWithoutComments).run {
                for ((contents, position) in markers) {
                    insert(position, contents)
                }
                toString()
            }
            assertEquals(
                expected = source.contents,
                actual = markedContents,
            )
        }
        val format = Json {
            prettyPrint = true
            @OptIn(ExperimentalSerializationApi::class)
            prettyPrintIndent = "  "
        }
        assertEquals(
            expected = expectedJsonPluginData,
            actual = format.encodeToString(result.declarations.withoutOrigin())
        )
    }
}

private val MultiLineCommentRegex = """/\*.*?\*/""".toRegex(RegexOption.DOT_MATCHES_ALL)
