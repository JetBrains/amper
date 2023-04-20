package org.jetbrains.deft.proto.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.deft.proto.gradle.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File


class MultipleModulesTests : WithTempDir {

    @field:TempDir
    override lateinit var tempDir: File

    @BeforeEach
    fun setUpGradleSettings() = setUpGradleProjectDir(tempDir)

    @Test
    fun twoModulesTest() {
        val runResult = runGradleWithModel(Models.twoModulesModel)
        val printKotlinInfo = runResult.task(":$printKotlinSourcesTask")
        assertEquals(TaskOutcome.SUCCESS, printKotlinInfo?.outcome)

        val extracted = runResult.output.extractSourceInfoOutput()

        // See, that module dependency will look like maven one,
        // but with group set as directory name, thus - [tempDir] name.
        assertEquals(
            """
:module1
  common:
   depends()
   sourceDirs(common)
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
  myAppJvmMain:
   depends(commonMain,common)
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppJvmTest:
   depends(commonTest)
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
:module2
  common:
   depends()
   sourceDirs(common)
   lang(api=null version=null progressive=false features=)
   implDeps(${tempDir.name}:module1:unspecified)
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
  myAppJvmMain:
   depends(commonMain,common)
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppJvmTest:
   depends(commonTest)
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
            """.trimIndent(),
            extracted
        )
    }

    @Test
    fun twoModulesTwoFragmentsTest() {
        val runResult = runGradleWithModel(Models.twoModulesTwoFragmentsModel)
        val printKotlinInfo = runResult.task(":$printKotlinSourcesTask")
        assertEquals(TaskOutcome.SUCCESS, printKotlinInfo?.outcome)

        val extracted = runResult.output.extractSourceInfoOutput()

        // See, that module dependency will look like maven one,
        // but with group set as directory name, thus - [tempDir] name.
        assertEquals(
            """
:module1
  common:
   depends()
   sourceDirs(common)
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
  jvm:
   depends(common)
   sourceDirs(jvm)
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppJvmMain:
   depends(commonMain,jvm)
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppJvmTest:
   depends(commonTest)
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
:module2
  common:
   depends()
   sourceDirs(common)
   lang(api=null version=null progressive=false features=)
   implDeps(${tempDir.name}:module1:unspecified)
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
  jvm:
   depends(common)
   sourceDirs(jvm)
   lang(api=null version=null progressive=false features=)
   implDeps(${tempDir.name}:module1:unspecified)
  myAppJvmMain:
   depends(commonMain,jvm)
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppJvmTest:
   depends(commonTest)
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
            """.trimIndent(),
            extracted
        )
    }

}