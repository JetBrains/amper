/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.amperFailure
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.*
import org.jetbrains.amper.gradle.util.MockModel
import org.jetbrains.amper.gradle.util.MockPotatoModule
import org.jetbrains.amper.gradle.util.getMockModelName
import org.jetbrains.amper.gradle.util.withDebug
import org.jetbrains.kotlin.gradle.utils.ProviderDelegate
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.properties.PropertyDelegateProvider


class MockModelInit : ModelInit by Models

// A way to reference "not yet built" model in tests.
class MockModelHandle(val name: String, val builder: MockModel.(Path) -> Unit)

/**
 * A hack to pass behaviour (hence, model builders) from tests to plugin.
 */
object Models : ModelInit {
    private lateinit var root: Path

    private val modelsMap = mutableMapOf<String, MockModelHandle>()

    private fun mockModel(builder: MockModel.(Path) -> Unit) =
        PropertyDelegateProvider<Models, ProviderDelegate<MockModelHandle>> { _, property ->
            val modelHandle = MockModelHandle(property.name, builder).apply { modelsMap[property.name] = this }
            ProviderDelegate { modelHandle }
        }

    override val name = "test"

    context(ProblemReporterContext)
    override fun getModel(root: Path): Result<MockModel> {
        val modelName = getMockModelName()
        if (modelName == null) {
            problemReporter.reportError(GradleTestBundle.message("no.mock.model.name", withDebug))
            return amperFailure()
        }
        Models.root = root
        val modelHandle = modelsMap[modelName]
        if (modelHandle == null) {
            problemReporter.reportError(GradleTestBundle.message("no.mock.model.found", modelName))
            return amperFailure()
        }
        val modelBuilder = modelHandle.builder
        return Result.success(MockModel(modelHandle.name).apply { modelBuilder(root) })
    }

    context(ProblemReporterContext)
    override fun getModel(root: PsiFile, project: Project): Result<Model> = getModel(root.virtualFile.toNioPath())

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
                    addPart(JvmPart(mainClass = "MainKt"))
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

    val kotlinPartModel by mockModel {
        module(it.moduleYaml) {
            artifact(
                "myApp",
                setOf(Platform.JVM),
                leafFragment {
                    addPart(
                        KotlinPart(
                            languageVersion = "1.9",
                            apiVersion = "1.9",
                            debug = null,
                            progressiveMode = true,
                            languageFeatures = listOf("InlineClasses"),
                            optIns = listOf("org.mylibrary.OptInAnnotation"),
                            serialization = null
                        ),
                    )
                    addPart(JvmPart(mainClass = "MainKt"))
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
                    addPart(
                        AndroidPart(
                            "android-31", "24", 17
                        )
                    )
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
                    addPart(JvmPart(mainClass = "MainKt"))
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
                    addPart(JvmPart(mainClass = "MainKt"))
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

    private fun MockPotatoModule.singleFragmentJvmArtifact() {
        artifact(
            "myApp",
            setOf(Platform.JVM),
            leafFragment {
                platforms.add(Platform.JVM)
                addPart(JvmPart(mainClass = "MainKt"))
            }
        )
    }
}
