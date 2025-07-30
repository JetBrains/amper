/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolder
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.Resolver
import org.jetbrains.amper.dependency.resolution.diagnostics.Message
import org.jetbrains.amper.dependency.resolution.diagnostics.Severity
import org.jetbrains.amper.dependency.resolution.diagnostics.SimpleDiagnosticDescriptor
import org.jetbrains.amper.dependency.resolution.diagnostics.detailedMessage
import org.jetbrains.amper.dependency.resolution.getDefaultFileCacheBuilder
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.assertEqualsWithDiff
import org.junit.jupiter.api.TestInfo
import org.opentest4j.AssertionFailedError
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeLines
import kotlin.io.path.writeText
import kotlin.test.assertIs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

abstract class BaseModuleDrTest {
    protected open val testGoldenFilesRoot: Path = Dirs.amperSourcesRoot.resolve("frontend/dr/testData/goldenFiles")
    protected val testDataRoot: Path = Dirs.amperSourcesRoot.resolve("frontend/dr/testData/projects")

    private val defaultMessagesCheck: (DependencyNode) -> Unit = { node ->
        val messages = node.messages.defaultFilterMessages()
        assertTrue(messages.isEmpty(), "There must be no messages for $this:\n${messages.joinToString("\n") { it.detailedMessage }}")
    }

    protected suspend fun doTestByFile(
        testInfo: TestInfo,
        aom: Model,
        resolutionInput: ResolutionInput,
        verifyMessages: Boolean = true,
        module: String? = null,
        fragment: String? = null,
        messagesCheck: (DependencyNode) -> Unit = defaultMessagesCheck
    ): DependencyNode {
        val fileName = "${testInfo.testMethod.get().name.replace(" ", "_")}.tree.txt"
        val expected = getGoldenFileText(fileName, fileDescription = "Golden file for resolved tree")
        return withActualDump(testGoldenFilesRoot.resolve(fileName)) {
            doTest(aom, resolutionInput, verifyMessages, expected, module, fragment, messagesCheck)
        }
    }

    protected suspend fun doTest(
        aom: Model,
        resolutionInput: ResolutionInput,
        verifyMessages: Boolean = true,
        @Language("text") expected: String? = null,
        module: String? = null,
        fragment: String? = null,
        messagesCheck: (DependencyNode) -> Unit = defaultMessagesCheck
    ): DependencyNode {
        val resolutionInputCopy = resolutionInput.copy(fileCacheBuilder = cacheBuilder(Dirs.userCacheRoot))

        val graph =
            with(moduleDependenciesResolver) {
                aom.modules.resolveDependencies(resolutionInputCopy)
            }

//        graph.verifyGraphConnectivity()
        if (verifyMessages) {
            graph.distinctBfsSequence().forEach {
                messagesCheck(it)
            }
        }

        val subGraph = when {
            module != null && fragment != null ->
                DependencyNodeHolder(
                    "Fragment '$module.$fragment' dependencies",
                    graph.fragmentDeps(module, fragment),
                    emptyContext(resolutionInput.fileCacheBuilder, resolutionInput.spanBuilder)
                )
            module != null -> graph.moduleDeps(module)
            else -> graph
        }

        expected?.let {
            assertEquals(expected, subGraph)
        }
        return subGraph
    }

    protected fun cacheBuilder(cacheRoot: Path): FileCacheBuilder.() -> Unit = {
        getDefaultFileCacheBuilder(cacheRoot).invoke(this)
        readOnlyExternalRepositories = emptyList()
    }

    protected fun assertEquals(@Language("text") expected: String, root: DependencyNode, forMavenNode: MavenCoordinates? = null) =
        assertEqualsWithDiff(expected.trimEnd().lines(), root.prettyPrint(forMavenNode).trimEnd().lines())

    protected suspend fun downloadAndAssertFiles(
        testInfo: TestInfo,
        root: DependencyNode,
        withSources: Boolean = false,
        checkAutoAddedDocumentation: Boolean = true
    ) {
        Resolver().downloadDependencies(root, withSources)
        assertFiles(
            testInfo,
            root,
            withSources,
            checkExistence = true,
            checkAutoAddedDocumentation = checkAutoAddedDocumentation
        )
    }

    protected suspend fun downloadAndAssertFiles(
        files: List<String>,
        root: DependencyNode,
        withSources: Boolean = false,
        checkAutoAddedDocumentation: Boolean = true
    ) {
        Resolver().downloadDependencies(root, withSources)
        assertFiles(
            files,
            root,
            withSources,
            checkExistence = true,
            checkAutoAddedDocumentation = checkAutoAddedDocumentation
        )
    }

    protected fun assertFiles(
        testInfo: TestInfo,
        root: DependencyNode,
        withSources: Boolean = false,
        checkExistence: Boolean = false,
        checkAutoAddedDocumentation: Boolean = true
    ) {
        val fileName = "${testInfo.testMethod.get().name.replace(" ", "_")}.files.txt"
        val expected = getGoldenFileText(fileName, fileDescription = "Golden file for files")
        withActualDump(testGoldenFilesRoot.resolve(fileName)) {
            assertFiles(expected.trim().lines(), root, withSources, checkExistence, checkAutoAddedDocumentation)
        }
    }

    // todo (AB) : Reuse utility methods from dependence-resolution test module
    protected fun assertFiles(
        files: List<String>, root: DependencyNode,
        withSources: Boolean = false,
        checkExistence: Boolean = false,// could be set to true only in case dependency files were downloaded by caller already
        checkAutoAddedDocumentation: Boolean = true // auto-added documentation files are skipped fom check if this flag is false.
    ) {
        root.distinctBfsSequence()
            .filterIsInstance<MavenDependencyNode>()
            .flatMap { it.dependency.files(withSources) }
            .filterNot { !checkAutoAddedDocumentation && it.isAutoAddedDocumentation }
            .mapNotNull { runBlocking { it.getPath() } }
            .sortedBy { it.name }
            .toSet()
            .let {
                assertEqualsWithDiff(files, it.map { file -> file.name })
                if (checkExistence) {
                    it.forEach {
                        check(it.exists()) {
                            "File $it was returned from dependency resolution, but is missing on disk"
                        }
                    }
                }
            }
    }

    protected fun getGoldenFileText(fileName: String, fileDescription: String): String {
        val goldenFile = testGoldenFilesRoot / fileName
        if (!goldenFile.exists()) fail("$fileDescription $goldenFile doesn't exist")
        return goldenFile
            .readText()
            .replace("#kotlinVersion", UsedVersions.kotlinVersion)
            .replace("#composeDefaultVersion", UsedVersions.composeVersion)
            .trim()
    }

    companion object {
        fun List<Message>.defaultFilterMessages(): List<Message> =
            filter { "Downloaded " !in it.message && "Resolved " !in it.message }

        internal fun DependencyNode.verifyOwnMessages(filterMessages: List<Message>.() -> List<Message> = { defaultFilterMessages() }) {
            val messages = this.messages.filterMessages()
            assertTrue(
                messages.isEmpty(),
                "There must be no messages for $this:\n${messages.joinToString("\n") { it.detailedMessage }}"
            )
        }

        internal inline fun <T> withActualDump(expectedResultPath: Path? = null, block: () -> T): T {
            return try {
                block()
            } catch (e: AssertionFailedError) {
                if (e.isExpectedDefined && e.isActualDefined && expectedResultPath != null) {
                    val actualResultPath = expectedResultPath.parent.resolve(expectedResultPath.fileName.name + ".tmp")
                    when(val actualValue = e.actual.value) {
                        is List<*> -> actualResultPath.writeLines(actualValue.map { it.toString().replaceVersionsWithVariables() })
                        is String -> actualResultPath.writeText(actualValue.replaceVersionsWithVariables())
                        else -> { /* do nothing */ }
                    }
                }
                throw e
            }.also {
                expectedResultPath?.parent?.resolve(expectedResultPath.fileName.name + ".tmp")?.deleteIfExists()
            }
        }

        private fun String.replaceVersionsWithVariables(): String =
            replaceArtifactFilenames(
                filePrefix = "kotlin-stdlib",
                version = UsedVersions.kotlinVersion,
                versionVariableName = "kotlinVersion",
            )
                .replaceCoordinateVersionWithReference(
                    groupPrefix = "org.jetbrains.kotlin",
                    artifactPrefix = "kotlin-",
                    version = UsedVersions.kotlinVersion,
                    versionVariableName = "kotlinVersion",
                )
                .replaceCoordinateVersionWithReference(
                    groupPrefix = "org.jetbrains.compose",
                    artifactPrefix = "",
                    version = UsedVersions.composeVersion,
                    versionVariableName = "composeDefaultVersion",
                )

        private fun String.replaceArtifactFilenames(
            filePrefix: String,
            version: String,
            versionVariableName: String,
        ): String = replace(Regex("""${Regex.escape(filePrefix)}.*-${Regex.escape(version)}\.(jar|aar|klib)""")) {
            it.value.replace(version, "#$versionVariableName")
        }
        private fun String.replaceCoordinateVersionWithReference(
            groupPrefix: String,
            artifactPrefix: String,
            version: String,
            versionVariableName: String,
        ): String = replace(Regex("""${Regex.escape(groupPrefix)}[^:]*:${Regex.escape(artifactPrefix)}[^:]*:([\w.\-]+ -> )?${Regex.escape(version)}""")) {
            it.value.replace(version, "#$versionVariableName")
        }
    }

    internal inline fun <reified MessageT : Message> assertTheOnlyNonInfoMessage(
        root: DependencyNode,
        severity: Severity
    ): MessageT {
        val messages = root.children.single().messages.defaultFilterMessages()
        val message = messages.singleOrNull()
        assertNotNull(message, "A single error message is expected, but found: ${messages.toSet()}")
        assertIs<MessageT>(message, "Unexpected error message")
        assertEquals(
            severity,
            message.severity,
            "Unexpected severity of the error message"
        )
        return message
    }

    internal fun assertTheOnlyNonInfoMessage(
        root: DependencyNode,
        diagnostic: SimpleDiagnosticDescriptor,
        severity: Severity = diagnostic.defaultSeverity,
        transitively: Boolean = false
    ) {
        val nodes = if (transitively) root.distinctBfsSequence() else sequenceOf(root)
        val messages = nodes.flatMap{ it.children.flatMap { it.messages.defaultFilterMessages() } }
        val message = messages.singleOrNull()
        assertNotNull(message, "A single error message is expected, but found: ${messages.toSet()}")
        assertEquals(
            diagnostic.id,
            message.id,
            "Unexpected error message"
        )
        assertEquals(
            severity,
            message.severity,
            "Unexpected severity of the error message"
        )
    }
}
