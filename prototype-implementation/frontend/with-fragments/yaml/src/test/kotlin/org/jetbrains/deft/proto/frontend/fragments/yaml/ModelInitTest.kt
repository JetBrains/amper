package org.jetbrains.deft.proto.frontend.fragments.yaml

import org.jetbrains.deft.proto.frontend.PotatoModuleDependency
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ModelInitTest {
    @Test
    fun `load model`() {
        val modelRoot = checkNotNull(ModelInitTest::class.java.getResource("testModel")?.path)
        val model = YamlFragmentsModelInit().getModel(Path(modelRoot))
        assertContentEquals(listOf("server", "common", "client"), model.modules.map { it.userReadableName })
        val clientModule = checkNotNull(model.modules.find { it.userReadableName == "client" })
        val commonModule = checkNotNull(model.modules.find { it.userReadableName == "common" })
        val serverModule = checkNotNull(model.modules.find { it.userReadableName == "server" })

        val clientModuleDependency = clientModule.fragments
            .first { it.name == "common" }
            .externalDependencies
            .first { it is PotatoModuleDependency } as PotatoModuleDependency
        with(clientModuleDependency) {
            assertEquals(commonModule, model.module)
        }

        val serverModuleDependency = serverModule.fragments
            .first { it.name == "jvm" }
            .externalDependencies
            .first { it is PotatoModuleDependency } as PotatoModuleDependency
        with(serverModuleDependency) {
            assertEquals(commonModule, model.module)
        }
    }
}