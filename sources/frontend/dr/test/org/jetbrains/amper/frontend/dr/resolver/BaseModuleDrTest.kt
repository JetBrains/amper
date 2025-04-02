/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.dependency.resolution.detailedMessage
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolder
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.Message
import org.jetbrains.amper.dependency.resolution.Resolver
import org.jetbrains.amper.dependency.resolution.getDefaultFileCacheBuilder
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.assertEqualsWithDiff
import org.junit.jupiter.api.TestInfo
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.sequences.forEach
import kotlin.test.assertEquals
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
        return doTest(aom, resolutionInput, verifyMessages, expected, module, fragment, messagesCheck)
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
            module != null && fragment != null -> DependencyNodeHolder("Fragment '$module.$fragment' dependencies", graph.fragmentDeps(module, fragment), emptyContext {})
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

    protected fun assertEquals(@Language("text") expected: String, root: DependencyNode) =
        assertEqualsWithDiff(expected.trimEnd().lines(), root.prettyPrint().trimEnd().lines())

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
        assertFiles(expected.trim().lines(), root, withSources, checkExistence, checkAutoAddedDocumentation)
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
            .trim()
    }

    companion object {
        fun List<Message>.defaultFilterMessages(): List<Message> =
            filter { "Downloaded " !in it.text && "Resolved " !in it.text }

        internal fun DependencyNode.verifyOwnMessages(filterMessages: List<Message>.() -> List<Message> = { defaultFilterMessages() }) {
            val messages = this.messages.filterMessages()
            assertTrue(
                messages.isEmpty(),
                "There must be no messages for $this:\n${messages.joinToString("\n") { it.detailedMessage }}"
            )
        }
    }
}
