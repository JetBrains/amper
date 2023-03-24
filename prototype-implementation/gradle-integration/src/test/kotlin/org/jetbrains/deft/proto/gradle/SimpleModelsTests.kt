package org.jetbrains.deft.proto.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.deft.proto.gradle.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

        val parsedSourceSets = parseSourceSetInfo(runResult)
        assertTrue(parsedSourceSets.containsKey("common")) { "No common source set" }
        assertTrue(parsedSourceSets["commonMain"]!!.contains("common")) { "commonMain does not depend on common. " +
                "Actual: ${parsedSourceSets["commonMain"]!!}" }
    }

    @Test
    fun twoFragmentJvmTest() {
        val runResult = runGradleWithModel(Models.jvmTwoFragmentModel)
        assertEquals(TaskOutcome.SUCCESS, runResult.task(":$printKotlinSourcesTask")?.outcome)

        val parsedSourceSets = parseSourceSetInfo(runResult)
        assertTrue(parsedSourceSets.containsKey("common")) { "No common source set" }
        assertTrue(parsedSourceSets["commonMain"]?.contains("common") ?: false) { "commonMain does not depend on common. " +
                "All: $parsedSourceSets" }
        assertTrue(parsedSourceSets.containsKey("jvm")) { "No jvm source set" }
        assertTrue(parsedSourceSets["myAppJVMMain"]?.contains("jvm") ?: false) { "myAppJVMMain does not depend on jvm. " +
                "All: $parsedSourceSets" }
    }

    @Test
    fun threeFragmentModelTest() {
        val runResult = runGradleWithModel(Models.threeFragmentModel)
        assertEquals(TaskOutcome.SUCCESS, runResult.task(":$printKotlinSourcesTask")?.outcome)

        val parsedSourceSets = parseSourceSetInfo(runResult)
        assertTrue(parsedSourceSets.containsKey("common")) { "No common source set" }
        assertTrue(parsedSourceSets["commonMain"]?.contains("common") ?: false) { "commonMain does not depend on common. " +
                "All: $parsedSourceSets" }
        assertTrue(parsedSourceSets.containsKey("jvm")) { "No jvm source set" }
        assertTrue(parsedSourceSets["myAppJVMMain"]?.contains("jvm") ?: false) { "myAppJVMMain does not depend on jvm. " +
                "All: $parsedSourceSets" }
        assertTrue(parsedSourceSets.containsKey("ios")) { "No common source set" }
        assertTrue(parsedSourceSets["myAppJVMMain"]?.contains("ios") ?: false) { "myAppJVMMain does not depend on common. " +
                "All: $parsedSourceSets" }
    }

}