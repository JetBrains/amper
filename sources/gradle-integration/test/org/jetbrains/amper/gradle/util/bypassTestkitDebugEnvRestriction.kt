/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.util

import org.gradle.tooling.BuildLauncher
import org.jetbrains.amper.frontend.ModelInit
import org.jetbrains.amper.gradle.MockModelHandle

private const val MOCK_MODEL_ENV = "MOCK_MODEL"

fun getMockModelName(): String? = System.getProperty(MOCK_MODEL_ENV)
//        if (withDebug)
//        else System.getenv(MOCK_MODEL_ENV)

fun BuildLauncher.withMockModel(mockModel: MockModelHandle): BuildLauncher = apply {
//    if (withDebug) {
        System.setProperty(MOCK_MODEL_ENV, mockModel.name)
        System.setProperty(ModelInit.MODEL_NAME_PROPERTY, "test")
//    } else {
//        withEnvironment(mapOf(
//                MOCK_MODEL_ENV to mockModel.name,
//                ModelInit.MODEL_NAME_ENV to "test"
//        ))
//    }
}
