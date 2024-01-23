/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import TestUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class JdkDownloaderTest {
    @Test
    fun downloadForAllPlatforms() {
        runBlocking {
            coroutineScope {
                for (os in JdkDownloader.OS.entries) {
                    for (arch in JdkDownloader.Arch.entries) {
                        val url = JdkDownloader.getUrl(os, arch)
                        launch(Dispatchers.IO) {
                            println("Checking $url")
                            JdkDownloader.getJdkHome(AmperUserCacheRoot(TestUtil.userCacheRoot), url)
                        }
                    }
                }
            }
        }
    }
}
