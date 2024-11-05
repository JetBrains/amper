/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.util.BuildType
import java.nio.file.Path
import kotlin.io.path.div

object IosConventions {

    /**
     * the single default Kotlin iOS framework name that is built per ios/app.
     */
    const val KOTLIN_FRAMEWORK_NAME = "KotlinModules.framework"

    const val COMPOSE_RESOURCES_CONTENT_DIR_NAME = "compose-resources"

    data class Context(
        val buildRootPath: Path,
        val moduleName: String,
        val buildType: BuildType,
        val platform: Platform,
    )

    context(Context)
    fun getAppFrameworkDirectory(): Path =
        getConventionalXcodeIntegrationDirectory() / "frameworks"

    context(Context)
    fun getAppFrameworkPath(): Path =
        getAppFrameworkDirectory() / KOTLIN_FRAMEWORK_NAME

    context(Context)
    fun getComposeResourcesDirectory(): Path =
        getConventionalXcodeIntegrationDirectory() / COMPOSE_RESOURCES_CONTENT_DIR_NAME

    context(Context)
    private fun getConventionalXcodeIntegrationDirectory(): Path =
        buildRootPath / "xci" / moduleName / platform.pretty / buildType.value
}