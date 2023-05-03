package org.jetbrains.deft.proto.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.deft.proto.gradle.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File


class AndroidArtifactTests : WithTempDir {

    @field:TempDir
    override lateinit var tempDir: File

    @BeforeEach
    fun setUpGradleSettings() = setUpGradleProjectDir(tempDir)

    @Test
    fun kotlinFragmentPartTest() {
        val runResult = runGradleWithModel(Models.singleFragmentAndroidModel)
        val printKotlinInfo = runResult.task(":$printKotlinSourcesTask")
        assertEquals(TaskOutcome.SUCCESS, printKotlinInfo?.outcome)

        val extracted = runResult.output.extractSourceInfoOutput()

        assertEquals(
            """
:
  common:
   depends()
   sourceDirs(src/common/kotlin,common/src)
   lang(api=null version=null progressive=false features=)
   implDeps()
  commonMain:
   depends(common)
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  commonTest:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppAndroidAndroidTest:
   depends(commonTest)
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppAndroidAndroidTestDebug:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppAndroidAndroidTestRelease:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppAndroidDebug:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppAndroidDebugAndroidTest:
   depends()
   sourceDirs(src/myAppAndroidDebugAndroidTest/kotlin)
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppAndroidDebugUnitTest:
   depends()
   sourceDirs(src/myAppAndroidDebugUnitTest/kotlin)
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppAndroidMain:
   depends(commonMain)
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppAndroidRelease:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppAndroidReleaseUnitTest:
   depends()
   sourceDirs(src/myAppAndroidReleaseUnitTest/kotlin)
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppAndroidTest:
   depends(commonTest)
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppAndroidTestDebug:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppAndroidTestFixtures:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppAndroidTestFixturesDebug:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppAndroidTestFixturesRelease:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppAndroidTestRelease:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
            """.trimIndent(),
            extracted
        )
    }

}