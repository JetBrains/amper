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
:
  common:
   depends()
   sourceDirs(src/common/kotlin,common/src)
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
  jvmMain:
   depends(commonMain,common)
   sourceDirs(src/jvmMain/java)
   lang(api=null version=null progressive=false features=)
   implDeps()
  jvmTest:
   depends(commonTest)
   sourceDirs(src/jvmTest/java)
   lang(api=null version=null progressive=false features=)
   implDeps()
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
:
  common:
   depends()
   sourceDirs(src/common/kotlin,common/src)
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
  jvm:
   depends(common)
   sourceDirs(src/jvm/kotlin,jvm/src)
   lang(api=null version=null progressive=false features=)
   implDeps()
  jvmMain:
   depends(commonMain,jvm)
   sourceDirs(src/jvmMain/java)
   lang(api=null version=null progressive=false features=)
   implDeps()
  jvmTest:
   depends(commonTest)
   sourceDirs(src/jvmTest/java)
   lang(api=null version=null progressive=false features=)
   implDeps()
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
:
  common:
   depends()
   sourceDirs(src/common/kotlin,common/src)
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
  ios:
   depends(common)
   sourceDirs(src/ios/kotlin,ios/src)
   lang(api=null version=null progressive=false features=)
   implDeps()
  iosArm64Main:
   depends(commonMain,ios)
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  iosArm64Test:
   depends(commonTest)
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  jvm:
   depends(common)
   sourceDirs(src/jvm/kotlin,jvm/src)
   lang(api=null version=null progressive=false features=)
   implDeps()
  jvmMain:
   depends(commonMain,jvm)
   sourceDirs(src/jvmMain/java)
   lang(api=null version=null progressive=false features=)
   implDeps()
  jvmTest:
   depends(commonTest)
   sourceDirs(src/jvmTest/java)
   lang(api=null version=null progressive=false features=)
   implDeps()
            """.trimIndent(),
            extracted
        )
    }

}