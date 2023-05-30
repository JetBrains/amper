package org.jetbrains.deft.proto.frontend.fragments.yaml

import org.jetbrains.deft.proto.frontend.PotatoModuleDependency
import kotlin.io.path.Path
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Ignore
class ModelInitTest {
    @Test
    fun `load model`() {
        val modelRoot = checkNotNull(ModelInitTest::class.java.getResource("testModel")?.path)
        val model = YamlFragmentsModelInit().getModel(Path(modelRoot))
        assertTrue(model.modules.map { it.userReadableName}.containsAll(listOf("common", "server", "client")))
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
