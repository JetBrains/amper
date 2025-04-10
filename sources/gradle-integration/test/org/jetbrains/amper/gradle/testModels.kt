/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle

import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.amperFailure
import org.jetbrains.amper.core.messages.BuildProblemImpl
import org.jetbrains.amper.core.messages.GlobalBuildProblemSource
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.NonIdealDiagnostic
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.ModelInit
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.schema.AndroidSettings
import org.jetbrains.amper.frontend.schema.AndroidVersion
import org.jetbrains.amper.frontend.schema.JvmSettings
import org.jetbrains.amper.frontend.schema.KotlinSettings
import org.jetbrains.amper.frontend.schema.KotlinVersion
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.gradle.util.MockModel
import org.jetbrains.amper.gradle.util.MockAmperModule
import org.jetbrains.amper.gradle.util.getMockModelName
import org.jetbrains.amper.gradle.util.withDebug
import java.nio.file.Path
import kotlin.collections.set
import kotlin.io.path.createDirectories
import kotlin.properties.PropertyDelegateProvider
import kotlin.reflect.KProperty

class MockModelInit : ModelInit by Models

// A way to reference "not yet built" model in tests.
class MockModelHandle(val name: String, val builder: MockModel.(Path) -> Unit)

/**
 * A hack to pass behaviour (hence, model builders) from tests to plugin.
 */
object Models : ModelInit {

    private val modelsMap = mutableMapOf<String, MockModelHandle>()

    private fun mockModel(builder: MockModel.(Path) -> Unit) =
        PropertyDelegateProvider<Models, ProviderDelegate<MockModelHandle>> { _, property ->
            val modelHandle = MockModelHandle(property.name, builder).apply { modelsMap[property.name] = this }
            ProviderDelegate { modelHandle }
        }

    override val name = "test"

    context(ProblemReporterContext)
    @OptIn(NonIdealDiagnostic::class)
    override fun getGradleAmperModel(
        rootProjectDir: Path,
        subprojectDirs: List<Path>,
    ): Result<MockModel> {
        val modelName = getMockModelName()
        if (modelName == null) {
            problemReporter.reportMessage(
                BuildProblemImpl(
                    buildProblemId = "no.mock.model.name",
                    source = GlobalBuildProblemSource,
                    message = GradleTestBundle.message("no.mock.model.name", withDebug),
                    level = Level.Fatal,
                )
            )
            return amperFailure()
        }
        val modelHandle = modelsMap[modelName]
        if (modelHandle == null) {
            problemReporter.reportMessage(
                BuildProblemImpl(
                    buildProblemId = "no.mock.model.found",
                    source = GlobalBuildProblemSource,
                    message = GradleTestBundle.message("no.mock.model.found", modelName),
                    level = Level.Fatal,
                )
            )
            return amperFailure()
        }
        val modelBuilder = modelHandle.builder
        return Result.success(MockModel(modelHandle.name, rootProjectDir).apply { modelBuilder(rootProjectDir) })
    }

    private val Path.moduleYaml: Path get() = resolve("module.yaml")

    // --- Models ----------------------------------------------------------------------------------------------------------
    // Need to be within [Models] class to be init.

    val commonFragmentModel by mockModel {
        module(it.moduleYaml) {
            artifact(
                "myApp",
                setOf(Platform.JVM),
                leafFragment {
                    platforms.add(Platform.JVM)
                    settings.jvm = JvmSettings().apply { mainClass = "MainKt" }
                }
            )
        }
    }


    val jvmTwoFragmentModel by mockModel {
        module(it.moduleYaml) {
            val jvm = leafFragment("jvm") {
                refines(fragment())
                platforms.add(Platform.JVM)
            }
            artifact(
                "myApp",
                setOf(Platform.JVM),
                jvm
            )
        }
    }

    val threeFragmentsSingleArtifactModel by mockModel {
        module(it.moduleYaml) {
            type = ProductType.LIB
            val common = fragment()
            val jvm = leafFragment("jvm") {
                refines(common)
                platforms.add(Platform.JVM)
            }
            val ios = leafFragment("ios") {
                refines(common)
                platforms.add(Platform.IOS_ARM64)
            }
            artifact(
                "myApp",
                setOf(Platform.JVM, Platform.IOS_ARM64),
                jvm, ios
            )
        }
    }

    val kotlinSettingsModel by mockModel {
        module(it.moduleYaml) {
            artifact(
                "myApp",
                setOf(Platform.JVM),
                leafFragment {
                    settings.kotlin = KotlinSettings().apply {
                        languageVersion = KotlinVersion.Kotlin19
                        debug = false
                        progressiveMode = true
                        languageFeatures = listOf(TraceableString("InlineClasses"))
                        optIns = listOf(TraceableString("org.mylibrary.OptInAnnotation"))
                    }
                    settings.jvm = JvmSettings().apply { mainClass = "MainKt" }

                    platforms.add(Platform.JVM)
                }
            )
        }
    }

    val singleFragmentAndroidModel by mockModel {
        module(it.moduleYaml) {
            artifact(
                "myApp",
                setOf(Platform.ANDROID),
                leafFragment {
                    platforms.add(Platform.ANDROID)
                    settings.android = AndroidSettings().apply {
                        compileSdk = AndroidVersion.VERSION_31
                        minSdk = AndroidVersion.VERSION_24
                        maxSdk = AndroidVersion.VERSION_34
                    }
                }
            )
        }
    }

    val twoModulesModel by mockModel {
        val module1 = module(it.resolve("module1/module.yaml").createDirectories()) {
            artifact(
                "myApp",
                setOf(Platform.JVM),
                leafFragment {
                    platforms.add(Platform.JVM)
                    settings.jvm = JvmSettings().apply { mainClass = "MainKt" }
                }
            )
        }
        module(it.resolve("module2/module.yaml").createDirectories()) {
            artifact(
                "myApp",
                setOf(Platform.JVM),
                leafFragment {
                    dependency(module1)
                    platforms.add(Platform.JVM)
                    settings.jvm = JvmSettings().apply { mainClass = "MainKt" }
                }
            )
        }
    }

    val twoModulesTwoFragmentsModel by mockModel {
        val module1 = module(it.resolve("module1/module.yaml").createDirectories()) {
            val jvm = leafFragment("jvm") {
                refines(fragment())
                platforms.add(Platform.JVM)
            }
            artifact(
                "myApp",
                setOf(Platform.JVM),
                jvm
            )
        }
        module(it.resolve("module2/module.yaml").createDirectories()) {
            val common = fragment {
                dependency(module1)
            }
            val jvm = leafFragment("jvm") {
                refines(common)
                dependency(module1)
                platforms.add(Platform.JVM)
            }
            artifact(
                "myApp",
                setOf(Platform.JVM),
                jvm
            )
        }
    }

    val twoDirectoryHierarchyModel by mockModel {
        module(it.resolve("outer/module.yaml").createDirectories()) {
            singleFragmentJvmArtifact()
        }
        module(it.resolve("outer/intermediate/inner/module.yaml").createDirectories()) {
            singleFragmentJvmArtifact()
        }
    }

    val threeDirectoryHierarchyModel by mockModel {
        module(it.resolve("outer/module.yaml").createDirectories()) {
            singleFragmentJvmArtifact()
        }
        module(it.resolve("outer/intermediate/inner1/module.yaml").createDirectories()) {
            singleFragmentJvmArtifact()
        }
        module(it.resolve("outer/inner2/module.yaml").createDirectories()) {
            singleFragmentJvmArtifact()
        }
    }

    private fun MockAmperModule.singleFragmentJvmArtifact() {
        artifact(
            "myApp",
            setOf(Platform.JVM),
            leafFragment {
                platforms.add(Platform.JVM)
                settings.jvm = JvmSettings().apply { mainClass = "MainKt" }
            }
        )
    }
}

// TODO this class was removed in KGP 2.1.0, so we copied it here as a quick fix.
//  We probably don't need this and should clean up.
private class ProviderDelegate<out T : Any>(
    private val defaultValueProvider: () -> T
) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return defaultValueProvider()
    }
}
