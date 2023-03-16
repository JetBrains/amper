package org.jetbrains.deft.proto.frontend.fragments.yaml

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals

class ModelInitTest {
    @Test
    fun `load model`() {
        val modelRoot = checkNotNull(ModelInitTest::class.java.getResource("testModel")?.path)
        val model = YamlFragmentsModelInit().getModel(Path(modelRoot))
        assertContentEquals(listOf("server", "common", "client"), model.modules.map { it.userReadableName })
    }
}