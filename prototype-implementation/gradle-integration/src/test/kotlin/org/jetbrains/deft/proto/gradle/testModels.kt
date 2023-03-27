package org.jetbrains.deft.proto.gradle

import org.jetbrains.deft.proto.frontend.AndroidArtifactPart
import org.jetbrains.deft.proto.frontend.KotlinFragmentPart
import org.jetbrains.deft.proto.frontend.ModelInit
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.gradle.util.MockModel
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

    override fun getModel(root: Path): MockModel {
        val modelName = getMockModelName()
            ?: error(
                "No mock model name specified via MOCK_MODEL env! " +
                        "\nDebug mode: $withDebug"
            )
        this.root = root
        val modelHandle: MockModelHandle = modelsMap[modelName] ?: error("No mock model with name $modelName is found!")
        val modelBuilder = modelHandle.builder
        return MockModel(modelHandle.name).apply { modelBuilder(root) }
    }

    private val Path.buildToml: Path get() = resolve("build.toml")

    // --- Models ----------------------------------------------------------------------------------------------------------
    // Need to be within [Models] class to be init.

    val commonFragmentModel by mockModel {
        module(it.buildToml) {
            val common = fragment("common")
            artifact(
                "myApp",
                setOf(Platform.JVM),
                common
            )
        }
    }


    val jvmTwoFragmentModel by mockModel {
        module(it.buildToml) {
            val common = fragment("common")
            val jvm = fragment("jvm") {
                refines(common)
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
            val common = fragment("common")
            val jvm = fragment("jvm") {
                refines(common)
            }
            val ios = fragment("ios") {
                refines(common)
            }
            artifact(
                "myApp",
                setOf(Platform.JVM),
                jvm, ios
            )
        }
    }

    val kotlinFragmentPartModel by mockModel {
        module(it.buildToml) {
            val common = fragment("common") {
                addPart(
                    KotlinFragmentPart(
                        "1.8",
                        "1.8",
                        true,
                        listOf("InlineClasses"),
                        listOf("org.mylibrary.OptInAnnotation"),
                    )
                )
            }
            artifact(
                "myApp",
                setOf(Platform.JVM),
                common
            )
        }
    }

    val singleFragmentAndroidModel by mockModel {
        module(it.buildToml) {
            val common = fragment("common")
            artifact(
                "myApp",
                setOf(Platform.ANDROID),
                common
            ) {
                addPart(AndroidArtifactPart(
                    "android-31",
                ))
            }
        }
    }

    val twoModulesModel by mockModel {
        val module1 = module(it.resolve("module1/build.toml").createDirectories()) {
            val common = fragment("common")
            artifact(
                "myApp",
                setOf(Platform.JVM),
                common
            )
        }
        module(it.resolve("module2/build.toml").createDirectories()) {
            val common = fragment("common") {
                dependency(module1)
            }
            artifact(
                "myApp",
                setOf(Platform.JVM),
                common
            )
        }
    }

    val twoModulesTwoFragmentsModel by mockModel {
        val module1 = module(it.resolve("module1/build.toml").createDirectories()) {
            val common = fragment("common")
            val jvm = fragment("jvm") {
                refines(common)
            }
            artifact(
                "myApp",
                setOf(Platform.JVM),
                jvm
            )
        }
        module(it.resolve("module2/build.toml").createDirectories()) {
            val common = fragment("common") {
                dependency(module1)
            }
            val jvm = fragment("jvm") {
                refines(common)
                dependency(module1)
            }
            artifact(
                "myApp",
                setOf(Platform.JVM),
                jvm
            )
        }
    }
}