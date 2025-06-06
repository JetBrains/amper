/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.compose

import com.intellij.util.asSafely
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.frontend.aomBuilder.chooseComposeVersion
import org.jetbrains.amper.gradle.android.AndroidAwarePart
import org.jetbrains.amper.gradle.base.AmperNamingConventions
import org.jetbrains.amper.gradle.base.PluginPartCtx
import org.jetbrains.amper.gradle.kmpp.KMPEAware
import org.jetbrains.amper.gradle.kmpp.KotlinAmperNamingConvention.kotlinSourceSet
import org.jetbrains.amper.gradle.moduleDir
import org.jetbrains.amper.gradle.tryRemove
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import java.io.File
import java.lang.reflect.Method

class ComposePluginPart(ctx: PluginPartCtx) : KMPEAware, AmperNamingConventions, AndroidAwarePart(ctx) {
    override val kotlinMPE: KotlinMultiplatformExtension =
        project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    override val needToApply by lazy {
        module.leafFragments.any { it.settings.compose.enabled }
    }

    // Highly dependent on compose version and ABI.
    // Need to implement API on compose plugin side.
    override fun applyBeforeEvaluate() {
        val composeVersion = chooseComposeVersion(model)!!
        val latestSupportedComposeVersion = "1.6.10"
        if (composeVersion != latestSupportedComposeVersion) {
            val extraInfo = if (composeVersion == UsedVersions.composeVersion) " (which is the new default)" else ""
            throw GradleException("Gradle-based Amper does not support Compose version $composeVersion$extraInfo. " +
                    "The only supported version is $latestSupportedComposeVersion. " +
                    "Please set the Compose version to $latestSupportedComposeVersion explicitly in your module.yaml " +
                    "settings, or try the standalone version of Amper.\nSee the release notes for more details: " +
                    "https://github.com/JetBrains/amper/releases/tag/v0.6.0")
        }
        val composeResourcesDir = module.moduleDir.resolve("composeResources").toFile()

        project.plugins.apply("org.jetbrains.kotlin.plugin.compose")
        project.plugins.apply("org.jetbrains.compose")

        // unfortunately, the latest compiler's native caches don't work properly with Compose 1.6.10
        project.extraProperties.set("kotlin.native.cacheKind", "none")

        // Clean old resources from source sets.
        kotlinMPE.sourceSets.all { it.resources.tryRemove { it.asSafely<File>()?.endsWith("composeResources") == true } }

        // Adjust task.
        adjustGenerateRClassTask(project, composeResourcesDir)

        // Adjust source sets.
        module.rootFragment.kotlinSourceSet?.apply {
            resources.srcDirs(composeResourcesDir)
            dependencies {
                implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
                implementation("org.jetbrains.compose.components:components-resources:$composeVersion")
            }
        }
        androidSourceSets?.findByName("main")
            ?.resources?.srcDirs(composeResourcesDir)

        // Adjust compose-android fonts copying tasks.
        // Execute in afterEvaluate, because these tasks are created when android variants are.
        project.afterEvaluate {
            listOf("Debug", "Release").forEach {
                val copyTask = project.tasks.findByName("copy${it}FontsToAndroidAssets")
                if (copyTask != null) trySetProperty(
                    copyTask::class.java.declaredMethods,
                    copyTask,
                    "getFrom",
                    composeResourcesDir
                )
            }
        }
    }

    private fun adjustGenerateRClassTask(project: Project, composeResourcesDir: File) {
        val genResTask = project.tasks.findByName("generateComposeResClass") ?: return
        fun <T> trySetResTaskProp(name: String, value: T) =
            trySetProperty(genResTask::class.java.declaredMethods, genResTask, name, value)

        trySetResTaskProp("getResDir", composeResourcesDir)
        trySetResTaskProp("getShouldGenerateResClass", true)

        // Tests hack to be able to import Res class with meaningful package.
        val testPackageName = System.getenv("AMPER_TEST_COMPOSE_PACKAGE_NAME")
        if (testPackageName != null) {
            trySetResTaskProp("getPackageName", testPackageName)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> trySetProperty(propGetters: Array<Method>, taskObj: Any, name: String, value: T) {
        propGetters.firstOrNull { it.name == name }
            ?.apply { isAccessible = true }
            ?.run { invoke(taskObj) as Property<T> }
            ?.apply { set(value) }
    }
}
