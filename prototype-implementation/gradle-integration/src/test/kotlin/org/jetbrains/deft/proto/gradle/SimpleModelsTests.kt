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
    fun setUpGradleSettings() = setUpGradleSettings(tempDir)

    @Test
    fun commonFragmentTest() {
        val runResult = runGradleWithModel(Models.commonFragmentModel)
        val printKotlinInfo = runResult.task(":$printKotlinSourcesTask")
        assertEquals(TaskOutcome.SUCCESS, printKotlinInfo?.outcome)

        val extracted = runResult.output.extractSourceInfoOutput()

        assertEquals(
            """
            common:depends ,lang api_null_version_null_progressive_false_features_
            commonMain:depends common,lang api_null_version_null_progressive_false_features_
            commonTest:depends ,lang api_null_version_null_progressive_false_features_
            myAppJVMMain:depends commonMain_common,lang api_null_version_null_progressive_false_features_
            myAppJVMTest:depends commonTest,lang api_null_version_null_progressive_false_features_
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
            common:depends ,lang api_null_version_null_progressive_false_features_
            commonMain:depends common,lang api_null_version_null_progressive_false_features_
            commonTest:depends ,lang api_null_version_null_progressive_false_features_
            jvm:depends common,lang api_null_version_null_progressive_false_features_
            myAppJVMMain:depends commonMain_jvm,lang api_null_version_null_progressive_false_features_
            myAppJVMTest:depends commonTest,lang api_null_version_null_progressive_false_features_
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
            common:depends ,lang api_null_version_null_progressive_false_features_
            commonMain:depends common,lang api_null_version_null_progressive_false_features_
            commonTest:depends ,lang api_null_version_null_progressive_false_features_
            ios:depends common,lang api_null_version_null_progressive_false_features_
            jvm:depends common,lang api_null_version_null_progressive_false_features_
            myAppJVMMain:depends commonMain_jvm_ios,lang api_null_version_null_progressive_false_features_
            myAppJVMTest:depends commonTest,lang api_null_version_null_progressive_false_features_
            """.trimIndent(),
            extracted
        )
    }

}