/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jdk.provisioning

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.system.Arch
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.test.Dirs
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class JdkDownloaderTest {
    @Test
    fun downloadForAllPlatforms() = runTest(timeout = 10.minutes) { // we've seen timeouts with 3 min on CI
        for (os in OsFamily.entries) {
            for (arch in Arch.entries) {
                launch(Dispatchers.IO) {
                    JdkDownloader.getJdk(AmperUserCacheRoot(Dirs.userCacheRoot), os, arch)
                }
            }
        }
    }
}