package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.helper.AbstractTestWithBuildFile
import org.jetbrains.deft.proto.core.system.SystemInfo
import org.jetbrains.deft.proto.frontend.helper.testParse
import kotlin.test.Test

class ReplacementsTest : AbstractTestWithBuildFile() {

    @Test
    fun `common library replacement`() {
        withBuildFile {
            testParse("17-compose-desktop-replacement", object : SystemInfo {
                override fun detect(): SystemInfo.Os {
                    return SystemInfo.Os(SystemInfo.OsFamily.MacOs, "X", SystemInfo.Arch.X64)
                }
            })
        }
    }

    @Test
    fun `jvm library replacement`() {
        withBuildFile {
            testParse("18-compose-desktop-jvm-replacement", object : SystemInfo {
                override fun detect(): SystemInfo.Os {
                    return SystemInfo.Os(SystemInfo.OsFamily.Linux, "3.14", SystemInfo.Arch.Arm64)
                }
            })
        }
    }
}
