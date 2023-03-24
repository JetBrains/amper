package org.jetbrains.deft.proto.gradle

import org.jetbrains.deft.proto.frontend.ModelInit
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.gradle.util.MockModel
import org.jetbrains.deft.proto.gradle.util.getMockModelName
import org.jetbrains.deft.proto.gradle.util.withDebug
import java.nio.file.Path


class MockModelInit : ModelInit by Models

// A way to reference "not yet built" model in tests.
class MockModelHandle(val name: String, val builder: MockModel.(Path) -> Unit)

/**
 * A hack to pass behaviour (hence, model builders) from tests to plugin.
 */
object Models : ModelInit {
    private lateinit var root: Path

    private val modelsMap = mutableMapOf<String, MockModelHandle>()
    fun mockModel(modelName: String, builder: MockModel.(Path) -> Unit) =
        MockModelHandle(modelName, builder).apply { modelsMap[modelName] = this }

    override fun getModel(root: Path): MockModel {
        val modelName = getMockModelName()
            ?: error(
                "No mock model name specified via MOCK_MODEL env! " +
                        "\nDebug mode: $withDebug"
            )
        this.root = root
        val modelHandle: MockModelHandle = modelsMap[modelName] ?: error("No mock model with name $modelName is found!")
        val modelBuilder = modelHandle.builder
        return MockModel(modelHandle.name).apply {
            modelBuilder(root)
        }
    }

    // --- Models ----------------------------------------------------------------------------------------------------------
    // Need to be within [Models] class to be init.

    val commonFragmentModel = mockModel("commonFragmentModel") {
        module(it.resolve("build.toml")) {
            val common = fragment("common")
            artifact(
                "myApp",
                setOf(Platform.JVM),
                common
            )
        }
    }


    val jvmTwoFragmentModel = mockModel("jvmTwoFragmentModel") {
        module(it.resolve("build.toml")) {
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

    val threeFragmentModel = mockModel("threeFragmentModel") {
        module(it.resolve("build.toml")) {
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

}