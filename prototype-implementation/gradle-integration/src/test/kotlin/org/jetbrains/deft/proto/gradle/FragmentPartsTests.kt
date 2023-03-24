package org.jetbrains.deft.proto.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.deft.proto.gradle.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FragmentPartsTests : WithTempDir {

    @field:TempDir
    override lateinit var tempDir: File

    @BeforeEach
    fun setUpGradleSettings() = setUpGradleSettings(tempDir)

    @Test
    fun kotlinFragmentPartTest() {
        val runResult = runGradleWithModel(Models.kotlinFragmentPartModel)
        val printKotlinInfo = runResult.task(":$printKotlinSourcesTask")
        assertEquals(TaskOutcome.SUCCESS, printKotlinInfo?.outcome)

        val extracted = runResult.output.extractSourceInfoOutput()

        assertEquals(
            """
            common:depends ,lang api_1.8_version_1.8_progressive_true_features_InlineClasses
            commonMain:depends common,lang api_1.8_version_1.8_progressive_true_features_InlineClasses
            commonTest:depends ,lang api_null_version_null_progressive_false_features_
            myAppJVMMain:depends commonMain_common,lang api_1.8_version_1.8_progressive_true_features_InlineClasses
            myAppJVMTest:depends commonTest,lang api_null_version_null_progressive_false_features_
            """.trimIndent(),
            extracted
        )
    }

}