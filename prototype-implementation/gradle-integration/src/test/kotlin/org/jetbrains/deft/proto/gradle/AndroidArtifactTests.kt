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
   sourceDirs(common)
   lang(api=null version=null progressive=false features=)
   implDeps()
  commonMain:
   depends(common)
   sourceDirs(commonMain)
   lang(api=null version=null progressive=false features=)
   implDeps()
  commonTest:
   depends()
   sourceDirs(commonTest)
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppANDROIDAndroidTest:
   depends(commonTest)
   sourceDirs(myAppANDROIDAndroidTest,androidTest)
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppANDROIDAndroidTestDebug:
   depends()
   sourceDirs(myAppANDROIDAndroidTestDebug,androidTestDebug)
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppANDROIDAndroidTestRelease:
   depends()
   sourceDirs(myAppANDROIDAndroidTestRelease,androidTestRelease)
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppANDROIDDebug:
   depends()
   sourceDirs(myAppANDROIDDebug,debug)
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppANDROIDDebugAndroidTest:
   depends()
   sourceDirs(myAppANDROIDDebugAndroidTest)
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppANDROIDDebugUnitTest:
   depends()
   sourceDirs(myAppANDROIDDebugUnitTest)
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppANDROIDMain:
   depends(commonMain)
   sourceDirs(myAppANDROIDMain,main)
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppANDROIDRelease:
   depends()
   sourceDirs(myAppANDROIDRelease,release)
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppANDROIDReleaseUnitTest:
   depends()
   sourceDirs(myAppANDROIDReleaseUnitTest)
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppANDROIDTest:
   depends(commonTest)
   sourceDirs(myAppANDROIDTest,test)
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppANDROIDTestDebug:
   depends()
   sourceDirs(myAppANDROIDTestDebug,testDebug)
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppANDROIDTestFixtures:
   depends()
   sourceDirs(myAppANDROIDTestFixtures,testFixtures)
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppANDROIDTestFixturesDebug:
   depends()
   sourceDirs(myAppANDROIDTestFixturesDebug,testFixturesDebug)
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppANDROIDTestFixturesRelease:
   depends()
   sourceDirs(myAppANDROIDTestFixturesRelease,testFixturesRelease)
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppANDROIDTestRelease:
   depends()
   sourceDirs(myAppANDROIDTestRelease,testRelease)
   lang(api=null version=null progressive=false features=)
   implDeps()
            """.trimIndent(),
            extracted
        )
    }

}