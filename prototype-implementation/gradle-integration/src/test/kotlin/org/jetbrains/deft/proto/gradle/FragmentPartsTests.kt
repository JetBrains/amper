package org.jetbrains.deft.proto.gradle


import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.deft.proto.gradle.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File


class FragmentPartsTests : WithTempDir {

    @field:TempDir
    override lateinit var tempDir: File

    @BeforeEach
    fun setUpGradleSettings() = setUpGradleProjectDir(tempDir)

    @Test
    fun kotlinFragmentPartTest() {
        val runResult = runGradleWithModel(Models.kotlinFragmentPartModel)
        val printKotlinInfo = runResult.task(":$printKotlinSourcesTask")
        assertEquals(TaskOutcome.SUCCESS, printKotlinInfo?.outcome)
        val extracted = runResult.output.extractSourceInfoOutput()

        assertEquals(
            """
:
  common:
   depends()
   sourceDirs(src/common/kotlin,common/src)
   lang(api=1.8 version=1.8 progressive=true features=InlineClasses)
   implDeps()
  commonMain:
   depends(common)
   sourceDirs()
   lang(api=1.8 version=1.8 progressive=true features=InlineClasses)
   implDeps()
  commonTest:
   depends()
   sourceDirs()
   lang(api=null version=null progressive=false features=)
   implDeps()
  myAppJvmMain:
   depends(commonMain,common)
   sourceDirs()
   lang(api=1.8 version=1.8 progressive=true features=InlineClasses)
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