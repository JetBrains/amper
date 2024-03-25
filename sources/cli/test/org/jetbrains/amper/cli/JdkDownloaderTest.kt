/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.test.TestUtil
import kotlin.test.Test

class JdkDownloaderTest {
    @Test
    fun downloadForAllPlatforms() {
        runBlocking {
            coroutineScope {
                for (os in JdkDownloader.OS.entries) {
                    for (arch in JdkDownloader.Arch.entries) {
                        launch(Dispatchers.IO) {
                            println("Checking $os / $arch")
                            JdkDownloader.getJdk(AmperUserCacheRoot(TestUtil.userCacheRoot), os, arch)
                        }
                    }
                }
            }
        }
    }
}
