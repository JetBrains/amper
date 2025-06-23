/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.jetbrains.amper.dependency.resolution.diagnostics.Message
import org.jetbrains.amper.dependency.resolution.diagnostics.Severity
import org.jetbrains.amper.dependency.resolution.diagnostics.SimpleDiagnosticDescriptor
import org.jetbrains.amper.dependency.resolution.diagnostics.SimpleMessage
import org.jetbrains.amper.dependency.resolution.diagnostics.detailedMessage
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.assertEqualsWithDiff
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.fail
import org.opentest4j.AssertionFailedError
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeLines
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class BaseDRTest {
    protected open val testDataPath: Path
        get() = Path("testData")

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
            root.verifyMessages(filterMessages)
        }
        expected?.let { assertEquals(expected, root) }
        return root
    }

    protected suspend fun doTestByFile(
        testInfo: TestInfo,
        root: DependencyNodeHolder,
        verifyMessages: Boolean = true,
        @Language("text") expected: String? = null,
        filterMessages: List<Message>.() -> List<Message> = { defaultFilterMessages() }
    ): DependencyNode {
        val goldenFile = testDataPath / "${testInfo.nameToGoldenFile()}.tree.txt"
        return withActualDump(goldenFile) {
            if (!goldenFile.exists()) fail("Golden file with the resolved tree '$goldenFile' doesn't exist")
            val expected = goldenFile.readText().replace("\r\n", "\n").trim()
            doTest(
                root,
                verifyMessages,
                expected,
                filterMessages,
            )
        }
    }

    private suspend fun doTestImpl(
        testInfo: TestInfo,
        dependency: List<String> = listOf(testInfo.nameToDependency()),
        scope: ResolutionScope = ResolutionScope.COMPILE,
        platform: Set<ResolutionPlatform> = setOf(ResolutionPlatform.JVM),
        repositories: List<Repository> = listOf(REDIRECTOR_MAVEN_CENTRAL),
        verifyMessages: Boolean = true,
        @Language("text") expected: String? = null,
        cacheBuilder: FileCacheBuilder.() -> Unit = cacheBuilder(Dirs.userCacheRoot),
        spanBuilder: SpanBuilderSource? = null,
        filterMessages: List<Message>.() -> List<Message> = { defaultFilterMessages() }
    ): DependencyNode =
        context(scope, platform, repositories, cacheBuilder, spanBuilder)
            .use { context ->
                val root = dependency.toRootNode(context)
                doTest(root, verifyMessages, expected, filterMessages)
            }

    protected suspend fun doTest(
        testInfo: TestInfo,
        dependency: String = testInfo.nameToDependency(),
        scope: ResolutionScope = ResolutionScope.COMPILE,
        platform: Set<ResolutionPlatform> = setOf(ResolutionPlatform.JVM),
        repositories: List<Repository> = listOf(REDIRECTOR_MAVEN_CENTRAL),
        verifyMessages: Boolean = true,
        @Language("text") expected: String? = null,
        cacheBuilder: FileCacheBuilder.() -> Unit = cacheBuilder(Dirs.userCacheRoot),
        filterMessages: List<Message>.() -> List<Message> = { defaultFilterMessages() },
        spanBuilder: SpanBuilderSource? = null,
    ): DependencyNode = doTest(
        testInfo,
        listOf(dependency),
        scope,
        platform,
        repositories,
        verifyMessages,
        expected,
        cacheBuilder,
        filterMessages,
        spanBuilder
    )

    protected suspend fun doTestByFile(
        testInfo: TestInfo,
        dependency: List<String> = listOf(testInfo.nameToDependency()),
        scope: ResolutionScope = ResolutionScope.COMPILE,
        platform: Set<ResolutionPlatform> = setOf(ResolutionPlatform.JVM),
        repositories: List<Repository> = listOf(REDIRECTOR_MAVEN_CENTRAL),
        verifyMessages: Boolean = true,
        cacheBuilder: FileCacheBuilder.() -> Unit = cacheBuilder(Dirs.userCacheRoot),
        filterMessages: List<Message>.() -> List<Message> = { defaultFilterMessages() },
        spanBuilder: SpanBuilderSource? = null,
    ): DependencyNode {
        val goldenFile = testDataPath / "${testInfo.nameToGoldenFile()}.tree.txt"
        return withActualDump(goldenFile) {
            if (!goldenFile.exists()) fail("Golden file with the resolved tree '$goldenFile' doesn't exist")
            val expected = goldenFile.readText().replace("\r\n", "\n").trim()
            doTest(
                testInfo = testInfo,
                dependency = dependency,
                scope,
                platform,
                repositories,
                verifyMessages,
                expected,
                cacheBuilder,
                filterMessages,
                spanBuilder
            )
        }
    }

    private inline fun <T> withActualDump(expectedResultPath: Path? = null, block: () -> T): T {
        return try {
            block()
        } catch (e: AssertionFailedError) {
            if (e.isExpectedDefined && e.isActualDefined && expectedResultPath != null) {
                val actualResultPath = expectedResultPath.parent.resolve(expectedResultPath.fileName.name + ".tmp")
                when(e.actual.value) {
                    is List<*> -> actualResultPath.writeLines((e.actual.value as List<*>).map { it.toString() })
                    is String -> actualResultPath.writeText(e.actual.value as String)
                    else -> { /* do nothing */ }
                }
            }
            throw e
        }.also {
            expectedResultPath?.parent?.resolve(expectedResultPath.fileName.name + ".tmp")?.deleteIfExists()
        }
    }

    protected suspend fun doTest(
        testInfo: TestInfo,
        dependency: List<String>,
        scope: ResolutionScope = ResolutionScope.COMPILE,
        platform: Set<ResolutionPlatform> = setOf(ResolutionPlatform.JVM),
        repositories: List<Repository> = listOf(REDIRECTOR_MAVEN_CENTRAL),
        verifyMessages: Boolean = true,
        @Language("text") expected: String? = null,
        cacheBuilder: FileCacheBuilder.() -> Unit = cacheBuilder(Dirs.userCacheRoot),
        filterMessages: List<Message>.() -> List<Message> = { defaultFilterMessages() },
        spanBuilder: SpanBuilderSource? = null,
    ): DependencyNode = doTestImpl(
        testInfo,
        dependency,
        scope,
        platform,
        repositories,
        verifyMessages,
        expected,
        cacheBuilder,
        spanBuilder,
        filterMessages,
    )


    protected fun context(
        scope: ResolutionScope = ResolutionScope.COMPILE,
        platform: Set<ResolutionPlatform> = setOf(ResolutionPlatform.JVM),
        repositories: List<Repository> = listOf(REDIRECTOR_MAVEN_CENTRAL),
        cacheBuilder: FileCacheBuilder.() -> Unit = cacheBuilder(Dirs.userCacheRoot),
        spanBuilder: SpanBuilderSource? = null,
    ) = Context {
        this.scope = scope
        this.platforms = platform
        this.repositories = repositories
        this.cache = cacheBuilder
        this.spanBuilder = spanBuilder ?: { NoopSpanBuilder.create() }
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
        assertEqualsWithDiff(expected.trimEnd().lines(), root.prettyPrint().trimEnd().lines())

    protected fun List<String>.toRootNode(context: Context) =
        DependencyNodeHolder(name = "root", children = map { it.toMavenNode(context) }, context)

    protected fun MavenCoordinates.toMavenNode(context: Context): MavenDependencyNode {
        val isBom = artifactId.startsWith("bom:")
        return MavenDependencyNode(context, groupId, artifactId, version, isBom = isBom)
    }

    protected fun String.toMavenCoordinates(): MavenCoordinates {
        val parts = split(":")
        val group = parts[0]
        val module = parts[1]
        val version = if (parts.size > 2) parts[2] else null
        return MavenCoordinates(group, module, version)
    }

    private fun String.toMavenNode(context: Context): MavenDependencyNode {
        val isBom = startsWith("bom:")
        val parts = removePrefix("bom:").trim().split(":")
        val group = parts[0]
        val module = parts[1]
        val version = if (parts.size > 2) parts[2] else null
        return MavenDependencyNode(context, group, module, version, isBom = isBom)
    }

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
            .mapNotNull { file -> runBlocking { file.getPath()?.let { file to it } } }
            .sortedBy { it.second.name }
            .toSet()
            .let {
                assertEqualsWithDiff(files, it.map { file -> file.second.name })
                if (checkExistence) {
                    it.forEach {
                        val file = it.first
                        val filePath = it.second
                        check(filePath.exists()) {
                            SimpleMessage(
                                text = "File '$filePath' was returned from dependency resolution, but is missing on disk",
                                id = "File is missing on disk",
                                childMessages = file.diagnosticsReporter.getMessages(),
                            ).detailedMessage
                        }
                    }
                }
            }
    }

    protected suspend fun assertFiles(
        testInfo: TestInfo,
        root: DependencyNode,
        withSources: Boolean = false,
        checkExistence: Boolean = false,
        checkAutoAddedDocumentation: Boolean = true,
    ) {
        val fileList = testDataPath / "${testInfo.nameToGoldenFile()}.files.txt"
        if (!fileList.exists()) fail("Golden file with the downloaded files '$fileList' doesn't exist")
        val expected = fileList.readText().trim().lines()
        withActualDump(fileList) {
            assertFiles(expected, root, withSources, checkExistence, checkAutoAddedDocumentation)
        }
    }

    protected suspend fun downloadAndAssertFiles(
        testInfo: TestInfo,
        root: DependencyNode,
        withSources: Boolean = false,
        checkAutoAddedDocumentation: Boolean = true,
        verifyMessages: Boolean = false,
        filterMessages: List<Message>.() -> List<Message> = { defaultFilterMessages() }
    ) {
        downloadDependencies(root, withSources, verifyMessages, filterMessages)
        assertFiles(
            testInfo,
            root,
            withSources,
            checkExistence = true,
            checkAutoAddedDocumentation = checkAutoAddedDocumentation
        )
    }

    protected suspend fun downloadAndAssertFiles(
        files: List<String>, root: DependencyNode, withSources: Boolean = false, checkAutoAddedDocumentation: Boolean = true,
        verifyMessages: Boolean = false, filterMessages: List<Message>.() -> List<Message> = { defaultFilterMessages() }
    ) {
        downloadDependencies(root, withSources, verifyMessages, filterMessages)
        assertFiles(
            files,
            root,
            withSources,
            checkExistence = true,
            checkAutoAddedDocumentation = checkAutoAddedDocumentation
        )
    }

    private suspend fun downloadDependencies(
        root: DependencyNode,
        withSources: Boolean,
        verifyMessages: Boolean,
        filterMessages: List<Message>.() -> List<Message>
    ) {
        Resolver().downloadDependencies(root, withSources)
        if (verifyMessages) {
            root.verifyMessages(filterMessages)
        }
    }

    companion object {
        internal val REDIRECTOR_MAVEN_CENTRAL = MavenRepository("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")
        internal val REDIRECTOR_INTELLIJ_SNAPSHOTS =
            MavenRepository("https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/snapshots")
        internal val REDIRECTOR_INTELLIJ_DEPS =
            MavenRepository("https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies")
        internal val REDIRECTOR_MAVEN_GOOGLE = MavenRepository("https://cache-redirector.jetbrains.com/maven.google.com")
        internal val REDIRECTOR_DL_GOOGLE_ANDROID =
            MavenRepository("https://cache-redirector.jetbrains.com/dl.google.com/dl/android/maven2")
        internal val REDIRECTOR_COMPOSE_DEV =
            MavenRepository("https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/public/p/compose/dev")
        internal val REDIRECTOR_JETBRAINS_KPM_PUBLIC =
            MavenRepository("https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/kpm/public")

        internal val MAVEN_LOCAL = MavenLocal

        fun List<Message>.defaultFilterMessages(): List<Message> =
            filter { it.severity > Severity.INFO }

        internal fun DependencyNode.verifyMessages(filterMessages: List<Message>.() -> List<Message> = { defaultFilterMessages() }) {
            distinctBfsSequence().forEach {
                it.verifyOwnMessages(filterMessages)
            }
        }

        internal fun DependencyNode.verifyOwnMessages(filterMessages: List<Message>.() -> List<Message> = { defaultFilterMessages() }) {
            val messages = this.messages.filterMessages()
            assertTrue(
                messages.isEmpty(),
                "There must be no messages for $this:\n${messages.joinToString("\n") { it.detailedMessage }}"
            )
        }

        internal fun MavenDependency.verifyOwnMessages(filterMessages: List<Message>.() -> List<Message> = { defaultFilterMessages() }) {
            val messages = this.messages.filterMessages()
            assertTrue(
                messages.isEmpty(),
                "There must be no messages for $this:\n${messages.joinToString("\n") { it.detailedMessage }}"
            )
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
}
