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
  androidAndroidTest:
   depends(commonTest)
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  androidAndroidTestDebug:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  androidAndroidTestRelease:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  androidDebug:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  androidDebugAndroidTest:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  androidDebugUnitTest:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  androidMain:
   depends(commonMain)
   sourceDirs(common)
   lang(api=null version=null progressive=false features=)
   implDeps()
  androidRelease:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  androidReleaseUnitTest:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  androidTest:
   depends(commonTest)
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  androidTestDebug:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  androidTestFixtures:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  androidTestFixturesDebug:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  androidTestFixturesRelease:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  androidTestRelease:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  commonMain:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  commonTest:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
            """.trimIndent(),
            extracted
        )
    }

}