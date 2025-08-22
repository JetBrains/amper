/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import com.intellij.openapi.util.Disposer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.intellij.lang.annotations.Language
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.plugins.schema.model.PluginDataRequest
import org.jetbrains.amper.plugins.schema.model.PluginDataResponse
import org.jetbrains.amper.test.TempDirExtension
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.io.path.Path
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
        pluginId: String = "test-plugin",
        block: TestBuilder.() -> Unit,
    ) {
        class Source(
            contents: String,
            packageName: String,
            val name: String,
        ) {
            val contents = """
                package $packageName
                import org.jetbrains.amper.*
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

        val disposable = Disposer.newDisposable()
        try {
            val classpath = System.getProperty("java.class.path")!!.split(File.pathSeparator)
            val stdlib = Path(classpath.first { "kotlin-stdlib-" in it })
            val extensibilityApi = Path(classpath.first { "amper-extensibility-api" in it })

            for (source in sources) {
                check(source.name.endsWith(".kt")) { "Kotlin source expected" }
                source.path.writeText(source.contentsWithoutComments)
            }

            val request = PluginDataRequest(
                plugins = listOf(
                    PluginDataRequest.PluginHeader(
                        pluginId = PluginData.Id(pluginId),
                        moduleExtensionSchemaName = schemaExtensionClassName?.let(PluginData::SchemaName),
                        sourceDir = tempDirExtension.path,
                    ),
                ),
                jdkPath = Path(System.getProperty("java.home")!!),
                librariesPaths = listOf(stdlib, extensibilityApi),
            )

            val result = runSchemaProcessor(disposable, request).first()
            val groupedErrors: Map<Path, List<PluginDataResponse.Error>> =
                result.errors.groupBy { it.filePath }
            for (source in sources) {
                val relevantErrors = groupedErrors[source.path].orEmpty()
                val markers = mutableListOf<Pair<String, Int>>()
                for (error in relevantErrors) {
                    markers += "/*{{*/" to error.textRange.first
                    markers += "/*}} ${error.message} */" to error.textRange.last
                }
                // Sorting all the markers to insert them one-by-one without from the end to avoid offsets recalculation
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
            assertEquals(
                expected = expectedJsonPluginData,
                actual = PrettyJson.encodeToString(result.pluginData)
            )
        } finally {
            Disposer.dispose(disposable)
        }
    }

    private fun ClassLoader.findUrlClassloader(): URLClassLoader? = when (this) {
        is URLClassLoader -> this
        else -> parent?.findUrlClassloader()
    }
}

private val MultiLineCommentRegex = """/\*.*?\*/""".toRegex(RegexOption.DOT_MATCHES_ALL)

private val PrettyJson = Json {
    prettyPrint = true
    @OptIn(ExperimentalSerializationApi::class)
    prettyPrintIndent = "  "
}