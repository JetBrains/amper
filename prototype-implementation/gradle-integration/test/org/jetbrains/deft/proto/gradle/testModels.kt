package org.jetbrains.deft.proto.gradle

import org.jetbrains.deft.proto.core.DeftException
import org.jetbrains.deft.proto.core.Result
import org.jetbrains.deft.proto.core.messages.ProblemReporterContext
import org.jetbrains.deft.proto.frontend.*
import org.jetbrains.deft.proto.gradle.util.MockModel
import org.jetbrains.deft.proto.gradle.util.MockPotatoModule
import org.jetbrains.deft.proto.gradle.util.getMockModelName
import org.jetbrains.deft.proto.gradle.util.withDebug
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
            return Result.failure(DeftException())
        }
        this.root = root
        val modelHandle = modelsMap[modelName]
        if (modelHandle == null) {
            problemReporter.reportError(GradleTestBundle.message("no.mock.model.found", modelName))
            return Result.failure(DeftException())
        }
        val modelBuilder = modelHandle.builder
        return Result.success(MockModel(modelHandle.name).apply { modelBuilder(root) })
    }

    private val Path.buildToml: Path get() = resolve("Pot.yaml")

    // --- Models ----------------------------------------------------------------------------------------------------------
    // Need to be within [Models] class to be init.

    val commonFragmentModel by mockModel {
        module(it.buildToml) {
            artifact(
                "myApp",
                setOf(Platform.JVM),
                leafFragment {
                    platforms.add(Platform.JVM)
                    addPart(
                        JavaPart(
                            mainClass = "MainKt",
                            null,
                            null,
                            null,
                            null
                        )
                    )
                }
            )
        }
    }


    val jvmTwoFragmentModel by mockModel {
        module(it.buildToml) {
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
        module(it.buildToml) {
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
        module(it.buildToml) {
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
                        ),
                    )
                    addPart(
                        JavaPart(
                            mainClass = "MainKt",
                            null,
                            null,
                            null,
                            null
                        )
                    )
                    platforms.add(Platform.JVM)
                }
            )
        }
    }

    val singleFragmentAndroidModel by mockModel {
        module(it.buildToml) {
            artifact(
                "myApp",
                setOf(Platform.ANDROID),
                leafFragment {
                    platforms.add(Platform.ANDROID)
                    addPart(
                        AndroidPart(
                            "android-31", "24", "17", 17
                        )
                    )
                }
            )
        }
    }

    val twoModulesModel by mockModel {
        val module1 = module(it.resolve("module1/Pot.yaml").createDirectories()) {
            artifact(
                "myApp",
                setOf(Platform.JVM),
                leafFragment {
                    platforms.add(Platform.JVM)
                    addPart(
                        JavaPart(
                            mainClass = "MainKt",
                            null,
                            null,
                            null,
                            null
                        )
                    )
                }
            )
        }
        module(it.resolve("module2/Pot.yaml").createDirectories()) {
            artifact(
                "myApp",
                setOf(Platform.JVM),
                leafFragment {
                    dependency(module1)
                    platforms.add(Platform.JVM)
                    addPart(
                        JavaPart(
                            mainClass = "MainKt",
                            null,
                            null,
                            null,
                            null
                        )
                    )
                }
            )
        }
    }

    val twoModulesTwoFragmentsModel by mockModel {
        val module1 = module(it.resolve("module1/Pot.yaml").createDirectories()) {
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
        module(it.resolve("module2/Pot.yaml").createDirectories()) {
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
        module(it.resolve("outer/Pot.yaml").createDirectories()) {
            singleFragmentJvmArtifact()
        }
        module(it.resolve("outer/intermediate/inner/Pot.yaml").createDirectories()) {
            singleFragmentJvmArtifact()
        }
    }

    val threeDirectoryHierarchyModel by mockModel {
        module(it.resolve("outer/Pot.yaml").createDirectories()) {
            singleFragmentJvmArtifact()
        }
        module(it.resolve("outer/intermediate/inner1/Pot.yaml").createDirectories()) {
            singleFragmentJvmArtifact()
        }
        module(it.resolve("outer/inner2/Pot.yaml").createDirectories()) {
            singleFragmentJvmArtifact()
        }
    }

    private fun MockPotatoModule.singleFragmentJvmArtifact() {
        artifact(
            "myApp",
            setOf(Platform.JVM),
            leafFragment {
                platforms.add(Platform.JVM)
                addPart(
                    JavaPart(
                        mainClass = "MainKt",
                        null,
                        null,
                        null,
                        null
                    )
                )
            }
        )
    }
}
