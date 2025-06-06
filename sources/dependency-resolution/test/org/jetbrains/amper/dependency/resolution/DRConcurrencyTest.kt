/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.test.Dirs
import org.junit.jupiter.api.TestInfo
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.*
import kotlin.io.path.appendText
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.fail

class DRConcurrencyTest : BaseDRTest() {

    companion object {
        private val cacheRoot: Path = Dirs.userCacheRoot
            .resolve(UUID.randomUUID().toString().padEnd(8, '0').substring(1..8))

        private val annotationJvmPath = cacheRoot.resolve(".m2.cache")
            .resolve("androidx").resolve("annotation")
            .resolve("annotation-jvm").resolve("1.6.0")
            .resolve("annotation-jvm-1.6.0.module")

        private const val annotationJvmCoordinates = "androidx.annotation:annotation-jvm:1.6.0"
    }

    /**
     * Check that computation of downloaded file hash doesn't clash with downloading itself in a concurrent environment.
     *
     * There is a small interval of time after the file was downloaded and moved to the target location and
     * before lock on that file was released.
     * Trying to read from a file at that period of time would fail.
     * DR internal methods that compute file cache should be resilient to this.
     */
    @Test
    fun `androidx_annotation annotation-jvm 1_6_0 computeHash`(testInfo: TestInfo) =
        doConcurrencyTest(testInfo) {
            annotationJvmPath.computeHash()
        }

    /**
     * Check that read from a downloaded file doesn't clash with downloading itself in a concurrent environment.
     *
     * There is a small interval of time after the file was downloaded and moved to the target location and
     * before lock on that file was released.
     * Trying to read from a file at that period of time would fail.
     * DR internal methods that read from a downloaded file cache should be resilient to this.
     */
    @Test
    fun `androidx_annotation annotation-jvm 1_6_0 readText`(testInfo: TestInfo) =
        doConcurrencyTest(testInfo) {
            val context = context(
                repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_MAVEN_GOOGLE).toRepositories(),
                cacheBuilder = cacheBuilder(cacheRoot))
            annotationJvmCoordinates.toMavenNode(context).dependency.moduleFile.readText()
        }

    /**
     * Check that read from a downloaded file doesn't clash with reading the file itself.
     *
     * There is a small interval of time after the file was downloaded and moved to the target location and
     * before lock on that file was released.
     * Trying to read from a file at that period of time would fail.
     * DR internal methods that read from a downloaded file cache should be resilient to this.
     */
    fun doConcurrencyTest(testInfo: TestInfo, block: suspend (Path) -> Unit) {
        runBlocking {
            coroutineScope {
                val testBody = async(Dispatchers.IO) {
                    for (i in 0..20) {
                        println("########### Iteration $i ")
                        doTest(
                            testInfo = testInfo,
                            dependency = annotationJvmCoordinates,
                            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_MAVEN_GOOGLE),
                            cacheBuilder = cacheBuilder(cacheRoot),
                            filterMessages = {
                                filter { "Downloaded " !in it.message && "Hashes don't match for" !in it.message }
                            }
                        )
                        // The File is updated/deleted to make it be downloaded again on the next iteration under the file lock
                        // (due to its absence or mismatching hashes).
                        // This is important because the test checks that hash computation and file locking don't clash.
                        if (i % 2 == 0) {
                            annotationJvmPath.takeIf { it.exists() }?.also { it.appendText("XXX") }
                        } else {
                            annotationJvmPath.deleteIfExists()
                        }
                    }
                }

                async(Dispatchers.IO) {
                    while (!testBody.isCompleted) {
                        try {
                            if (annotationJvmPath.exists()) {
                                block(annotationJvmPath)
                            }
                        } catch (_: NoSuchFileException) {
                            // Ignore this exception since testBody could have removed the file at the end of the iteration
                            continue
                        } catch (e: AmperDependencyResolutionException) {
                            if (e.message == "Path doesn't exist, download the file first") {
                                // Ignore this exception since testBody could have removed the file at the end of the iteration
                            } else {
                                fail("Unexpected exception on cache computation", e)
                            }
                            continue
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            fail("Unexpected exception on cache computation", e)
                        }
                    }
                }
            }
        }.invokeOnCompletion {
            // Test finished
        }
    }

    @AfterTest
    fun cleanUp() {
        if (cacheRoot != Dirs.userCacheRoot) {
            cacheRoot.deleteRecursively()
        }
    }
}
