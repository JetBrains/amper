package org.jetbrains.deft.proto.gradle.util

import org.gradle.testkit.runner.GradleRunner
import org.jetbrains.deft.proto.gradle.MockModelHandle

private const val MOCK_MODEL_ENV = "MOCK_MODEL"

fun getMockModelName() =
    if (withDebug) System.getProperty(MOCK_MODEL_ENV)
    else System.getenv(MOCK_MODEL_ENV)

fun GradleRunner.withMockModel(mockModel: MockModelHandle): GradleRunner = apply {
    if (withDebug) System.setProperty(MOCK_MODEL_ENV, mockModel.name)
    else withEnvironment(mapOf(MOCK_MODEL_ENV to mockModel.name))
}