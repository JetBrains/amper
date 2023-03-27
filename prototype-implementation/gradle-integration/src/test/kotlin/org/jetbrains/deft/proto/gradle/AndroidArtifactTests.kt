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
            common                         :depends(                ) sourceDirs(common          ) lang(api=null version=null progressive=false features=)
            commonMain                     :depends(common          ) sourceDirs(commonMain      ) lang(api=null version=null progressive=false features=)
            commonTest                     :depends(                ) sourceDirs(commonTest      ) lang(api=null version=null progressive=false features=)
            myAppANDROIDAndroidTest        :depends(commonTest      ) sourceDirs(myAppANDROIDAndroidTest,androidTest) lang(api=null version=null progressive=false features=)
            myAppANDROIDAndroidTestDebug   :depends(                ) sourceDirs(myAppANDROIDAndroidTestDebug,androidTestDebug) lang(api=null version=null progressive=false features=)
            myAppANDROIDAndroidTestRelease :depends(                ) sourceDirs(myAppANDROIDAndroidTestRelease,androidTestRelease) lang(api=null version=null progressive=false features=)
            myAppANDROIDDebug              :depends(                ) sourceDirs(myAppANDROIDDebug,debug) lang(api=null version=null progressive=false features=)
            myAppANDROIDDebugAndroidTest   :depends(                ) sourceDirs(myAppANDROIDDebugAndroidTest) lang(api=null version=null progressive=false features=)
            myAppANDROIDDebugUnitTest      :depends(                ) sourceDirs(myAppANDROIDDebugUnitTest) lang(api=null version=null progressive=false features=)
            myAppANDROIDMain               :depends(commonMain      ) sourceDirs(myAppANDROIDMain,main) lang(api=null version=null progressive=false features=)
            myAppANDROIDRelease            :depends(                ) sourceDirs(myAppANDROIDRelease,release) lang(api=null version=null progressive=false features=)
            myAppANDROIDReleaseUnitTest    :depends(                ) sourceDirs(myAppANDROIDReleaseUnitTest) lang(api=null version=null progressive=false features=)
            myAppANDROIDTest               :depends(commonTest      ) sourceDirs(myAppANDROIDTest,test) lang(api=null version=null progressive=false features=)
            myAppANDROIDTestDebug          :depends(                ) sourceDirs(myAppANDROIDTestDebug,testDebug) lang(api=null version=null progressive=false features=)
            myAppANDROIDTestFixtures       :depends(                ) sourceDirs(myAppANDROIDTestFixtures,testFixtures) lang(api=null version=null progressive=false features=)
            myAppANDROIDTestFixturesDebug  :depends(                ) sourceDirs(myAppANDROIDTestFixturesDebug,testFixturesDebug) lang(api=null version=null progressive=false features=)
            myAppANDROIDTestFixturesRelease:depends(                ) sourceDirs(myAppANDROIDTestFixturesRelease,testFixturesRelease) lang(api=null version=null progressive=false features=)
            myAppANDROIDTestRelease        :depends(                ) sourceDirs(myAppANDROIDTestRelease,testRelease) lang(api=null version=null progressive=false features=)
            """.trimIndent(),
            extracted
        )
    }

}