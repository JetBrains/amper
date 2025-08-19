/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import javassist.Modifier
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.DependencyGraph
import org.jetbrains.amper.dependency.resolution.DependencyGraph.Companion.toGraph
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.DependencyNodePlain
import org.jetbrains.amper.dependency.resolution.diagnostics.Message
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.assertEqualsWithDiff
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.reflections.Reflections
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import kotlin.test.assertTrue

class GraphSerializationTest: BaseModuleDrTest() {

    override val testGoldenFilesRoot: Path = super.testGoldenFilesRoot / "graphSerialization"

    val json = ModuleDependenciesResolverImpl.json

    @Test
    fun serializationTestJava(testInfo: TestInfo) = runSlowTest {
        val aom = getTestProjectModel("jvm-transitive-dependencies", testDataRoot)

        val appModuleGraph = doTestByFile(
            testInfo,
            aom,
            resolutionInput = ResolutionInput(
                DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                skipIncrementalCache = true,
                fileCacheBuilder = getAmperFileCacheBuilder(AmperUserCacheRoot(Dirs.userCacheRoot)),
            ),
            module = "D2",
        )

        assertGraphSerialization(appModuleGraph)
    }

    @Test
    fun serializationTestKmp(testInfo: TestInfo) = runSlowTest {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)

        val iosAppModuleDeps = doTestByFile(
            testInfo,
            aom,
            ResolutionInput(
                DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                skipIncrementalCache = true,
                fileCacheBuilder = getAmperFileCacheBuilder(AmperUserCacheRoot(Dirs.userCacheRoot)),
            ),
            module = "ios-app",
        )

        assertGraphSerialization(iosAppModuleDeps)
    }

    @Test
    fun serializationTestInvalidDependencies(testInfo: TestInfo) = runSlowTest {
        val aom = getTestProjectModel("jvm-invalid-dependencies", testDataRoot)

        // Run the test that calculates diagnostics for all invalid dependencies, including the diagnostic with stack trace
        val root = doTestByFile(
            testInfo,
            aom,
            resolutionInput = ResolutionInput(
                DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                skipIncrementalCache = true,
                fileCacheBuilder = getAmperFileCacheBuilder(AmperUserCacheRoot(Dirs.userCacheRoot)),
            ),
            verifyMessages = false
        )

        // todo (AB): Check that deserializedRoot contains the same diagnostics with throwable as root (or get rid od Throwable in diagnostic)

        val deserializedRoot = assertGraphSerialization(root)
//        root.children.single()
//            .children.filterIsInstance<DirectFragmentDependencyNode>()
//            .first { (it.dependencyNode as? MavenDependencyNode)?.group == "com.jetbrains.intellij.platform" }
//            .dependencyNode
//            .let { it as MavenDependencyNode }
//            .messages.filterIsInstance<WithThrowable>().single().throwable?.stackTrace?.toSet()?.map { it.toString() } ==
//                deserializedRoot.children.single()
//                    .children.filterIsInstance<DirectFragmentDependencyNode>()
//                    .first { (it.dependencyNode as? MavenDependencyNode)?.group == "com.jetbrains.intellij.platform" }
//                    .dependencyNode
//                    .let { it as MavenDependencyNode }
//                    .messages.filterIsInstance<WithThrowable>().single().throwable?.stackTrace?.toSet()?.map { it.toString() }
    }

    /**
     * @return deserialized graph
     */
    private fun assertGraphSerialization(root: DependencyNode): DependencyNode {
        val serializableGraph = root.toGraph()

        val encoded = json.encodeToString(serializableGraph)
        println(encoded)

        val decoded = json.decodeFromString(DependencyGraph.serializer(), encoded)

        val encodedOfDecoded = json.encodeToString(decoded)

        kotlin.test.assertEquals(
            encoded,
            encodedOfDecoded,
            "decoded string being encoded again differs from initially encoded"
        )

        val nodePlain = decoded.root.toNodePlain(decoded.graphContext)
        assertEqualsWithDiff(
            root.prettyPrint().lines(),
            nodePlain.prettyPrint().lines(),
            "decoded graph pretty print representation differs from the original graph"
        )

        return nodePlain
    }

    @Test
    fun `all classes implementing Message are annotated with Serializable and properly registered`() = runTest {
        checkPolymorphicRequirements(Message::class)
    }

    @Test
    fun `all classes implementing DependencyNodePlain are annotated with Serializable and properly registered`() = runTest {
        checkPolymorphicRequirements(DependencyNodePlain::class)
    }

    /**
     * Check that all classes implementing the given interface are annotated with [Serializable] and properly registered
     */
    @OptIn(ExperimentalSerializationApi::class)
   inline fun <reified T: Any> checkPolymorphicRequirements(
        kClass: KClass<T>,
        excludingSubClasses: List<KClass<out T>>? = null
    ) {
        assertTrue("An abstract class or interface is expected, but ${kClass.simpleName} was given") { kClass.isAbstract }

        val reflections = Reflections("org.jetbrains.amper")
        val allNonAbstractChildrenJava = reflections
            .getSubTypesOf(kClass.java)
            .filterNot { kClass ->
                kClass.isInterface
                        || Modifier.isAbstract(kClass.modifiers)
                        || (excludingSubClasses != null && excludingSubClasses.any { kClass.isAssignableFrom(it.java) })
            }

        val allNonAbstractChildrenKotlin = allNonAbstractChildrenJava.map { it.kotlin }

        val notSerializable =
            allNonAbstractChildrenKotlin.filter { it.annotations.none { it is Serializable } }.map { it.simpleName }.toSet()
        assertTrue("${kClass.simpleName} should has serializable children only, but the following are not: $notSerializable") {
            notSerializable.isEmpty()
        }

        val notRegistered = allNonAbstractChildrenKotlin.filter {
            json.serializersModule.getPolymorphic(kClass, it.jvmName) == null
        }
        assertTrue("Serializable classes implementing ${kClass.simpleName}, should be registered in a polymorphic serializer module: $notRegistered") {
            notRegistered.isEmpty()
        }
    }

    /**
     * Plain serializable nodes are used as a key in Map the same way as their sourcing counterparts from the origin dependency graph.
     * Nodes with the same content might belong to different resolution context and should be left separated,
     * this is why data classes are not suitable here.
     */
    @Test
    fun `all classes implementing DependencyNodePlain are not data classes`() = runTest {
        val kClass = DependencyNodePlain::class

        val reflections = Reflections("org.jetbrains.amper")
        val allNonAbstractChildrenJava = reflections
            .getSubTypesOf(kClass.java)
            .filterNot { it.isInterface || Modifier.isAbstract(it::class.java.modifiers) }

        val allNonAbstractChildrenKotlin = allNonAbstractChildrenJava.map { it.kotlin }

        val dataClasses =
            allNonAbstractChildrenKotlin.filter { it.isData }.map { it.simpleName }.toSet()
        assertTrue("${kClass.simpleName} should has no data class children, but the following are not: $dataClasses") {
            dataClasses.isEmpty()
        }
    }
}