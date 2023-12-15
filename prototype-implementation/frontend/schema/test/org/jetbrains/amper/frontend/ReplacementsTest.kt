/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.helper.aomTest
import org.jetbrains.amper.frontend.old.helper.TestWithBuildFile
import kotlin.test.Ignore
import kotlin.test.Test

class ReplacementsTest : TestWithBuildFile() {

    // TODO Fix
    @Test
    @Ignore
    fun `common library replacement`() {
        withBuildFile {
//            aomTest("17-compose-desktop-replacement", object : SystemInfo {
//                override fun detect(): SystemInfo.Os {
//                    return SystemInfo.Os(SystemInfo.OsFamily.MacOs, "X", SystemInfo.Arch.X64)
//                }
//            })
        }
    }

    // TODO Fix
    @Test
    @Ignore
    fun `jvm library replacement`() {
        withBuildFile {
//            aomTest("18-compose-desktop-jvm-replacement", object : SystemInfo {
//                override fun detect(): SystemInfo.Os {
//                    return SystemInfo.Os(SystemInfo.OsFamily.Linux, "3.14", SystemInfo.Arch.Arm64)
//                }
//            })
        }
    }
}
