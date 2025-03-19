/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
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
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.sequences.forEach
import kotlin.test.assertTrue

abstract class BaseModuleDrTest {

    protected val testDataRoot: Path = Dirs.amperSourcesRoot.resolve("frontend/dr/testData/projects")

    protected suspend fun doTest(
        aom: Model,
        resolutionInput: ResolutionInput,
        verifyMessages: Boolean = true,
        @Language("text") expected: String? = null,
        module: String? = null,
        fragment: String? = null,
        messagesCheck: (DependencyNode) -> Unit = { node ->
            val messages = node.messages.defaultFilterMessages()
            assertTrue(messages.isEmpty(), "There must be no messages for $this:\n${messages.joinToString("\n") { it.detailedMessage }}")
        }
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
        kotlin.test.assertEquals(expected, root.prettyPrint().trimEnd())

    protected suspend fun downloadAndAssertFiles(
        files: String,
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

    // todo (AB) : Reuse utility methods from dependence-resolution test module
    protected fun assertFiles(
        files: String, root: DependencyNode,
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
                kotlin.test.assertEquals(files, it.joinToString("\n") { it.name })
                if (checkExistence) {
                    it.forEach {
                        check(it.exists()) {
                            "File $it was returned from dependency resolution, but is missing on disk"
                        }
                    }
                }
            }
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
