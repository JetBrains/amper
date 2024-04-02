/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.android.gradle.tooling

import org.jetbrains.amper.android.AndroidBuildResult
import org.jetbrains.amper.android.RClassAndroidBuildResult
import com.android.build.gradle.api.BaseVariant

data class RClassAndroidBuildResultImpl(override val paths: List<String>) : RClassAndroidBuildResult

class RClassToolingModelBuilder : BaseAndroidToolingModelBuilder() {
    override fun canBuild(modelName: String): Boolean = RClassAndroidBuildResult::class.java.name == modelName

    override fun List<BaseVariant>.getArtifactsFromVariants(): List<String> = flatMap { it.outputs }
        .flatMap { it.processResourcesProvider.get().outputs.files.toList() }
        .map { it.toString() }

    override fun buildResult(paths: List<String>): AndroidBuildResult = RClassAndroidBuildResultImpl(paths)
}