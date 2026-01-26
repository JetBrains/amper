/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.android

import kotlinx.serialization.Serializable
import org.jetbrains.amper.serialization.paths.SerializablePath
import java.io.File

@Serializable
data class ResolvedDependency(
    val group: String,
    val artifact: String,
    val version: String,
    val path: SerializablePath
)

@Serializable
data class AndroidModuleData(
    val modulePath: String, // relative module path from root in Gradle format ":path:to:module"
    val moduleClasses: List<SerializablePath> = emptyList(),
    val resolvedAndroidRuntimeDependencies: List<ResolvedDependency> = listOf()
)

@Serializable
data class AndroidBuildRequest(
    /**
     * The root of the Amper project, which is necessary to parse a correct Amper model.
     */
    val root: SerializablePath,
    val phase: Phase,
    val modules: Set<AndroidModuleData> = setOf(),
    val buildTypes: Set<BuildType> = setOf(BuildType.Debug),

    /**
     * Module name, if not set, all modules will be built
     */
    val targets: Set<String> = setOf(),

    val sdkDir: SerializablePath? = null
) {
    enum class BuildType(val value: String) {
        Debug("debug"),
        Release("release")
    }

    enum class Phase {
        /**
         * generate R class and other things which is needed for compilation
         */
        Prepare,

        /**
         * build APK
         */
        Build,

        /**
         * Bundle AAB for Google Play Store
         */
        Bundle,

        /**
         * Get a mockable jar for unit tests
         */
        Test,
    }
}

typealias ProjectPath = String
typealias VariantName = String
typealias TaskName = String

interface ProcessResourcesProviderData : java.io.Serializable {

    val data: Map<ProjectPath, Map<VariantName, TaskName>>
}

interface MockableJarModel: java.io.Serializable {
    val file: File?
}
