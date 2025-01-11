/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.jetbrains.amper.test.Dirs
import org.junit.jupiter.api.TestInfo
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.test.assertTrue

abstract class BaseDRTest {

    protected suspend fun doTest(
        root: DependencyNodeHolder,
        verifyMessages: Boolean = true,
        @Language("text") expected: String? = null,
        filterMessages: List<Message>.() -> List<Message> = { defaultFilterMessages() }
    ): DependencyNode {
        val resolver = Resolver()
        resolver.buildGraph(root, ResolutionLevel.NETWORK)
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
        repositories: List<Repository> = listOf(REDIRECTOR_MAVEN_CENTRAL).toRepositories(),
        verifyMessages: Boolean = true,
        @Language("text") expected: String? = null,
        cacheBuilder: FileCacheBuilder.() -> Unit = cacheBuilder(Dirs.userCacheRoot),
        filterMessages: List<Message>.() -> List<Message> = { defaultFilterMessages() }
    ): DependencyNode =
        context(scope, platform, repositories, cacheBuilder)
            .use { context ->
                val root = dependency.toRootNode(context)
                runBlocking {
                    doTest(root, verifyMessages, expected, filterMessages)
                }
            }

    protected fun doTest(
        testInfo: TestInfo,
        dependency: String = testInfo.nameToDependency(),
        scope: ResolutionScope = ResolutionScope.COMPILE,
        platform: Set<ResolutionPlatform> = setOf(ResolutionPlatform.JVM),
        repositories: List<String> = listOf(REDIRECTOR_MAVEN_CENTRAL),
        verifyMessages: Boolean = true,
        @Language("text") expected: String? = null,
        cacheBuilder: FileCacheBuilder.() -> Unit = cacheBuilder(Dirs.userCacheRoot),
        filterMessages: List<Message>.() -> List<Message> = { defaultFilterMessages() }
    ): DependencyNode = doTest(testInfo, listOf(dependency), scope, platform, repositories, verifyMessages, expected, cacheBuilder, filterMessages)


    protected fun doTest(
        testInfo: TestInfo,
        dependency: List<String>,
        scope: ResolutionScope = ResolutionScope.COMPILE,
        platform: Set<ResolutionPlatform> = setOf(ResolutionPlatform.JVM),
        repositories: List<String> = listOf(REDIRECTOR_MAVEN_CENTRAL),
        verifyMessages: Boolean = true,
        @Language("text") expected: String? = null,
        cacheBuilder: FileCacheBuilder.() -> Unit = cacheBuilder(Dirs.userCacheRoot),
        filterMessages: List<Message>.() -> List<Message> = { defaultFilterMessages() }
    ): DependencyNode = doTestImpl(testInfo, dependency, scope, platform, repositories.toRepositories(), verifyMessages, expected, cacheBuilder, filterMessages)


    protected fun context(
        scope: ResolutionScope = ResolutionScope.COMPILE,
        platform: Set<ResolutionPlatform> = setOf(ResolutionPlatform.JVM),
        repositories: List<Repository> = listOf(REDIRECTOR_MAVEN_CENTRAL).toRepositories(),
        cacheBuilder: FileCacheBuilder.() -> Unit = cacheBuilder(Dirs.userCacheRoot),
    ) = Context {
        this.scope = scope
        this.platforms = platform
        this.repositories = repositories
        this.cache = cacheBuilder
    }

    protected fun cacheBuilder(cacheRoot: Path): FileCacheBuilder.() -> Unit = {
        getDefaultFileCacheBuilder(cacheRoot).invoke(this)
        readOnlyExternalRepositories = emptyList()
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
        DependencyNodeHolder(name ="root", children = map { it.toMavenNode(context) }, context)

    private fun String.toMavenNode(context: Context): MavenDependencyNode {
        val (group, module, version) = split(":")
        return MavenDependencyNode(context, group, module, version)
    }

    protected fun assertFiles(
        files: String, root: DependencyNode,
        withSources: Boolean = false,
        checkExistence: Boolean = false,// could be set to true only in case dependency files were downloaded by caller already
        checkAutoAddedDocumentation: Boolean = true // auto added documentation files are skipped fom check if this flag is false.
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

    protected suspend fun downloadAndAssertFiles(files: String, root: DependencyNode, withSources: Boolean = false, checkAutoAddedDocumentation: Boolean = true) {
        Resolver().downloadDependencies(root, withSources)
        assertFiles(files, root, withSources, checkExistence = true, checkAutoAddedDocumentation = checkAutoAddedDocumentation)
    }

    companion object {
        internal const val REDIRECTOR_MAVEN_CENTRAL = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2"
        internal const val REDIRECTOR_MAVEN_GOOGLE = "https://cache-redirector.jetbrains.com/maven.google.com"
        internal const val REDIRECTOR_DL_GOOGLE_ANDROID = "https://cache-redirector.jetbrains.com/dl.google.com/dl/android/maven2"
        internal const val REDIRECTOR_COMPOSE_DEV = "https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/public/p/compose/dev"
        internal const val REDIRECTOR_JETBRAINS_KPM_PUBLIC = "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/kpm/public"

        fun List<Message>.defaultFilterMessages(): List<Message> =
            filter { "Downloaded " !in it.text && "Resolved " !in it.text }
    }
}