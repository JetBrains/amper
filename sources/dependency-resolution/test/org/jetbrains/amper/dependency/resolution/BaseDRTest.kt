/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.jetbrains.amper.test.TestUtil
import org.junit.jupiter.api.TestInfo
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.test.assertTrue

abstract class BaseDRTest {

    protected fun doTest(
        root: DependencyNodeHolder,
        verifyMessages: Boolean = true,
        @Language("text") expected: String? = null,
        filterMessages: List<Message>.() -> List<Message> = { filter { "Downloaded from" !in it.text } }
    ): DependencyNode {
        val resolver = Resolver()
        runBlocking { resolver.buildGraph(root, ResolutionLevel.NETWORK) }
        root.verifyGraphConnectivity()
        if (verifyMessages) {
            root.distinctBfsSequence().forEach {
                val messages = it.messages.filterMessages()
                assertTrue(messages.isEmpty(), "There must be no messages for $it: $messages")
            }
        }
        expected?.let { assertEquals(expected, root) }
        return root
    }

    private fun doTestImpl(
        testInfo: TestInfo,
        dependency: List<String> = listOf(testInfo.nameToDependency()),
        scope: ResolutionScope = ResolutionScope.COMPILE,
        platform: Set<ResolutionPlatform> = setOf(ResolutionPlatform.JVM),
        repositories: List<Repository> = REDIRECTOR_MAVEN2.toRepositories(),
        verifyMessages: Boolean = true,
        @Language("text") expected: String? = null,
        cacheRoot: Path = TestUtil.userCacheRoot,
        filterMessages: List<Message>.() -> List<Message> = { filter { "Downloaded from" !in it.text } }
    ): DependencyNode =
        context(scope, platform, repositories, cacheRoot)
            .use { context ->
                val root = dependency.toRootNode(context)
                doTest(root, verifyMessages, expected, filterMessages)
            }

    protected fun doTest(
        testInfo: TestInfo,
        dependency: String = testInfo.nameToDependency(),
        scope: ResolutionScope = ResolutionScope.COMPILE,
        platform: Set<ResolutionPlatform> = setOf(ResolutionPlatform.JVM),
        repositories: List<String> = REDIRECTOR_MAVEN2,
        verifyMessages: Boolean = true,
        @Language("text") expected: String? = null,
        cacheRoot: Path = TestUtil.userCacheRoot,
        filterMessages: List<Message>.() -> List<Message> = { filter { "Downloaded from" !in it.text } }
    ): DependencyNode = doTest(testInfo, listOf(dependency), scope, platform, repositories, verifyMessages, expected, cacheRoot, filterMessages)

    protected fun doTest(
        testInfo: TestInfo,
        dependency: List<String>,
        scope: ResolutionScope = ResolutionScope.COMPILE,
        platform: Set<ResolutionPlatform> = setOf(ResolutionPlatform.JVM),
        repositories: List<String> = REDIRECTOR_MAVEN2,
        verifyMessages: Boolean = true,
        @Language("text") expected: String? = null,
        cacheRoot: Path = TestUtil.userCacheRoot,
        filterMessages: List<Message>.() -> List<Message> = { filter { "Downloaded from" !in it.text } }
    ): DependencyNode = doTestImpl(testInfo, dependency, scope, platform, repositories.toRepositories(), verifyMessages, expected, cacheRoot, filterMessages)


    protected fun context(
        scope: ResolutionScope = ResolutionScope.COMPILE,
        platform: Set<ResolutionPlatform> = setOf(ResolutionPlatform.JVM),
        repositories: List<Repository> = REDIRECTOR_MAVEN2.toRepositories(),
        cacheRoot: Path = TestUtil.userCacheRoot
    ) = Context{
        this.scope = scope
        this.platforms = platform
        this.repositories = repositories
        this.cache = {
            amperCache = cacheRoot.resolve(".amper")
            localRepositories = listOf(MavenLocalRepository(cacheRoot.resolve(".m2.cache")))
        }
    }

    protected fun DependencyNode.verifyGraphConnectivity() {
        val queue = LinkedList(listOf(this))
        val verified = mutableSetOf<DependencyNode>()
        while (queue.isNotEmpty()) {
            val node = queue.remove()
            node.children.forEach { assertTrue(node in it.parents, "Parents don't match") }
            verified.add(node)
            queue += node.children.filter { it !in verified }
        }
    }

    private fun assertEquals(@Language("text") expected: String, root: DependencyNode) =
        kotlin.test.assertEquals(expected, root.prettyPrint().trimEnd())

    protected fun List<String>.toRootNode(context: Context) =
        DependencyNodeHolder(name ="root", children = map { it.toMavenNode(context) })

    private fun String.toMavenNode(context: Context): MavenDependencyNode {
        val (group, module, version) = split(":")
        return MavenDependencyNode(context, group, module, version)
    }

    protected fun assertFiles(
        files: String, root: DependencyNode, withSources: Boolean = false,
        checkExistence: Boolean = false // could be set to true only in case dependency files were downloaded by caller already
    ) {
        root.distinctBfsSequence()
            .filterIsInstance<MavenDependencyNode>()
            .flatMap { it.dependency.files(withSources) }
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

    protected suspend fun downloadAndAssertFiles(files: String, root: DependencyNode, withSources: Boolean = false) {
        Resolver().downloadDependencies(root)
        assertFiles(files, root, withSources, checkExistence = true)
    }

    companion object {
        internal val REDIRECTOR_MAVEN2 = listOf("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
    }
}