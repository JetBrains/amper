package org.jetbrains.amper.gradle.util

import org.gradle.testkit.runner.GradleRunner
import org.jetbrains.amper.frontend.ModelInit
import org.jetbrains.amper.gradle.MockModelHandle

private const val MOCK_MODEL_ENV = "MOCK_MODEL"

fun getMockModelName(): String? =
        if (withDebug) System.getProperty(MOCK_MODEL_ENV)
        else System.getenv(MOCK_MODEL_ENV)

fun GradleRunner.withMockModel(mockModel: MockModelHandle): GradleRunner = apply {
    if (withDebug) {
        System.setProperty(MOCK_MODEL_ENV, mockModel.name)
        System.setProperty(ModelInit.MODEL_NAME_PROPERTY, "test")
    } else {
        withEnvironment(mapOf(
                MOCK_MODEL_ENV to mockModel.name,
                ModelInit.MODEL_NAME_ENV to "test"
        ))
    }
}
