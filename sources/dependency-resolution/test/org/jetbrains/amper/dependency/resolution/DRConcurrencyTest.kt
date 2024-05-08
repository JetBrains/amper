/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.test.TestUtil
import org.junit.jupiter.api.TestInfo
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.*
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.fail

@ExperimentalPathApi
class DRConcurrencyTest: BaseDRTest() {

    companion object {
        private val cacheRoot: Path = TestUtil.userCacheRoot
            .resolve(UUID.randomUUID().toString().padEnd(8, '0').substring(1..8))

        private val annotationJvmPath = cacheRoot.resolve(".m2.cache")
            .resolve("androidx").resolve("annotation")
            .resolve("annotation-jvm").resolve("1.6.0")
            .resolve("annotation-jvm-1.6.0.module")
    }

    /**
     * Check that computation of downloaded file hash doesn't clash with downloading itself in a concurrent environment.
     *
     * There is a small interval of time after the file was downloaded and moved to target location and
     * before lock on that file was released. Trying to read from file at that period of time would fail.
     * DR internal methods that compute file cache should be resilient to this.
     */
    @Test
    fun `androidx_annotation annotation-jvm 1_6_0`(testInfo: TestInfo) {
        runBlocking {
            coroutineScope {
                val testBody = async(Dispatchers.IO) {
                    for (i in 0..20) {
                        println("########### Iteration $i ")
                        doTest(
                            testInfo,
                            repositories = REDIRECTOR_MAVEN2 + "https://cache-redirector.jetbrains.com/maven.google.com",
                            cacheRoot = cacheRoot
                        )
                        // The File is deleted to make it be downloaded again on the next iteration under the file lock.
                        // This is important because the test checks that hash computation and file locking don't clash.
                        annotationJvmPath.deleteIfExists()
                    }
                }

                async(Dispatchers.IO) {
                    while (!testBody.isCompleted) {
                        if (annotationJvmPath.exists()) {
                            try {
                                annotationJvmPath.computeHash()
                            } catch (e: NoSuchFileException) {
                                // Ignore this exception since testBody could have removed the file at the end of the iteration
                                continue
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                fail("Unexpected exception on cache computation", e)
                            }
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
        if (cacheRoot != TestUtil.userCacheRoot) {
            cacheRoot.deleteRecursively()
        }
    }
}