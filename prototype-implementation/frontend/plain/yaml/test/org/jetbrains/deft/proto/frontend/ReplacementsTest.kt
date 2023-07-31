package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.helper.testParse
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test

class ReplacementsTest {
    @TempDir
    lateinit var tempDir: Path

    private val buildFile get() = object: BuildFileAware {
        override val buildFile: Path
            get() = tempDir.resolve("build.yaml")
    }

    @Test
    fun `common library replacement`() {
        with(buildFile) {
            testParse("17-compose-desktop-replacement", object : OsDetector {
                override fun detect(): OsDetector.Os {
                    return OsDetector.Os.macosX64
                }
            })
        }
    }

    @Test
    fun `jvm library replacement`() {
        with(buildFile) {
            testParse("18-compose-desktop-jvm-replacement", object : OsDetector {
                override fun detect(): OsDetector.Os {
                    return OsDetector.Os.linuxArm64
                }
            })
        }
    }
}