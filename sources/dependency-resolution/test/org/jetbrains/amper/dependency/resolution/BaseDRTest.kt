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
import kotlin.io.path.name
import kotlin.test.assertTrue

abstract class BaseDRTest {

    protected fun doTest(
        testInfo: TestInfo,
        dependency: String = testInfo.nameToDependency(),
        scope: ResolutionScope = ResolutionScope.COMPILE,
        platform: ResolutionPlatform = ResolutionPlatform.JVM,
        repositories: List<String> = REDIRECTOR_MAVEN2,
        verifyMessages: Boolean = true,
        @Language("text") expected: String? = null,
        cacheRoot: Path = TestUtil.userCacheRoot,
        filterMessages: List<Message>.() -> List<Message> = { filter { "Downloaded from" !in it.text } }
    ): DependencyNode {
        context(scope, platform, repositories, cacheRoot).use { context ->
            val root = dependency.toRootNode(context)
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
    }

    protected fun context(
        scope: ResolutionScope = ResolutionScope.COMPILE,
        platform: ResolutionPlatform = ResolutionPlatform.JVM,
        repositories: List<String> = REDIRECTOR_MAVEN2,
        cacheRoot: Path = TestUtil.userCacheRoot
    ) = Context{
        this.scope = scope
        this.platforms = setOf(platform)
        this.repositories = repositories
        this.cache = {
            amperCache = cacheRoot.resolve(".amper")
            localRepositories = listOf(MavenLocalRepository(cacheRoot.resolve(".m2.cache")))
        }
    }

    private fun DependencyNode.verifyGraphConnectivity() {
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

    private fun assertFiles(files: String, root: DependencyNode) {
        root.distinctBfsSequence()
            .mapNotNull { it as? MavenDependencyNode }
            .flatMap { it.dependency.files }
            .mapNotNull { runBlocking { it.getPath()?.name } }
            .sorted()
            .toSet()
            .let { kotlin.test.assertEquals(files, it.joinToString("\n")) }
    }

    protected fun List<String>.toRootNode(context: Context) =
        ModuleDependencyNode(context, "root", map { it.toMavenNode(context) })

    private fun String.toMavenNode(context: Context): MavenDependencyNode {
        val (group, module, version) = split(":")
        return MavenDependencyNode(context, group, module, version)
    }


    private fun String.toRootNode(context: Context) = ModuleDependencyNode(context, "root", listOf(toMavenNode(context)))

    companion object {
        internal val REDIRECTOR_MAVEN2 = listOf("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
    }
}