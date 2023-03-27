package org.jetbrains.deft.proto.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.deft.proto.gradle.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SimpleModelsTests : WithTempDir {

    @field:TempDir
    override lateinit var tempDir: File

    @BeforeEach
    fun setUpGradleSettings() = setUpGradleProjectDir(tempDir)

    @Test
    fun commonFragmentTest() {
        val runResult = runGradleWithModel(Models.commonFragmentModel)
        val printKotlinInfo = runResult.task(":$printKotlinSourcesTask")
        assertEquals(TaskOutcome.SUCCESS, printKotlinInfo?.outcome)

        val extracted = runResult.output.extractSourceInfoOutput()

        assertEquals(
            """
            common      :depends(                ) sourceDirs(common          ) lang(api=null version=null progressive=false features=)
            commonMain  :depends(common          ) sourceDirs(commonMain      ) lang(api=null version=null progressive=false features=)
            commonTest  :depends(                ) sourceDirs(commonTest      ) lang(api=null version=null progressive=false features=)
            myAppJVMMain:depends(commonMain,common) sourceDirs(myAppJVMMain    ) lang(api=null version=null progressive=false features=)
            myAppJVMTest:depends(commonTest      ) sourceDirs(myAppJVMTest    ) lang(api=null version=null progressive=false features=)
            """.trimIndent(),
            extracted
        )
    }

    @Test
    fun twoFragmentJvmTest() {
        val runResult = runGradleWithModel(Models.jvmTwoFragmentModel)
        assertEquals(TaskOutcome.SUCCESS, runResult.task(":$printKotlinSourcesTask")?.outcome)

        val extracted = runResult.output.extractSourceInfoOutput()

        assertEquals(
            """
            common      :depends(                ) sourceDirs(common          ) lang(api=null version=null progressive=false features=)
            commonMain  :depends(common          ) sourceDirs(commonMain      ) lang(api=null version=null progressive=false features=)
            commonTest  :depends(                ) sourceDirs(commonTest      ) lang(api=null version=null progressive=false features=)
            jvm         :depends(common          ) sourceDirs(jvm             ) lang(api=null version=null progressive=false features=)
            myAppJVMMain:depends(commonMain,jvm  ) sourceDirs(myAppJVMMain    ) lang(api=null version=null progressive=false features=)
            myAppJVMTest:depends(commonTest      ) sourceDirs(myAppJVMTest    ) lang(api=null version=null progressive=false features=)
            """.trimIndent(),
            extracted
        )
    }

    @Test
    fun threeFragmentsSingleArtifactModel() {
        val runResult = runGradleWithModel(Models.threeFragmentsSingleArtifactModel)
        assertEquals(TaskOutcome.SUCCESS, runResult.task(":$printKotlinSourcesTask")?.outcome)

        val extracted = runResult.output.extractSourceInfoOutput()

        assertEquals(
            """
            common      :depends(                ) sourceDirs(common          ) lang(api=null version=null progressive=false features=)
            commonMain  :depends(common          ) sourceDirs(commonMain      ) lang(api=null version=null progressive=false features=)
            commonTest  :depends(                ) sourceDirs(commonTest      ) lang(api=null version=null progressive=false features=)
            ios         :depends(common          ) sourceDirs(ios             ) lang(api=null version=null progressive=false features=)
            jvm         :depends(common          ) sourceDirs(jvm             ) lang(api=null version=null progressive=false features=)
            myAppJVMMain:depends(commonMain,jvm,ios) sourceDirs(myAppJVMMain    ) lang(api=null version=null progressive=false features=)
            myAppJVMTest:depends(commonTest      ) sourceDirs(myAppJVMTest    ) lang(api=null version=null progressive=false features=)
            """.trimIndent(),
            extracted
        )
    }

}