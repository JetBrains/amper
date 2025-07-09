/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.plugins.schema.model.SchemaName
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.contexts.PathCtx
import org.jetbrains.amper.frontend.contexts.PlatformCtx
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.frontend.tree.helpers.diagnoseModuleRead
import org.jetbrains.amper.frontend.tree.helpers.testModuleRead
import org.jetbrains.amper.frontend.tree.helpers.testRefineModule
import org.jetbrains.amper.frontend.tree.helpers.testRefineModuleWithTemplates
import org.jetbrains.amper.test.golden.GoldenTestBase
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.div

class TreeTests : GoldenTestBase(Path(".") / "testResources" / "valueTree") {

    @Test
    fun `all settings read`() =
        testModuleRead("all-module-settings")

    @Test
    fun `all settings merge for jvm`() = testRefineModule(
        "all-module-settings",
        selectedContexts = platformCtxs("jvm"),
        expectPostfix = "-merge-jvm-result.json",
    )

    @Test
    fun `all settings merge for android`() = testRefineModule(
        "all-module-settings",
        selectedContexts = platformCtxs("android"),
        expectPostfix = "-merge-android-result.json",
    )

    @Test
    fun `merge with templates`() = testRefineModuleWithTemplates(
        "with-templates",
        selectedContexts = { platformCtxs("jvm") + PathCtx(it, null) },
    )

    class CustomPluginSchema : SchemaNode() {
        val foo: Int by value()
        val bar: String by value()
    }

    @Test
    fun `read module file with custom properties`() = testModuleRead(
        "with-custom-properties",
        types = SchemaTypingContext(
            pluginData = listOf(PluginData(
                id = PluginData.Id("myPlugin"),
                pluginModuleRoot = ".",
                moduleExtensionSchemaName = SchemaName("com.example.CustomPluginSchema"),
                classTypes = listOf(
                    PluginData.ClassData(
                        name = SchemaName("com.example.CustomPluginSchema"),
                        properties = listOf(
                            PluginData.ClassData.Property(
                                name = "foo",
                                type = PluginData.Type.IntType(),
                            ),
                            PluginData.ClassData.Property(
                                name = "bar",
                                type = PluginData.Type.StringType(),
                            ),
                        )
                    )
                ),
            ))
        )
    )

    @Test
    fun `read module file with custom properties diagnostics`() = diagnoseModuleRead(
        "with-custom-properties-diagnostics",
        types = SchemaTypingContext(
            pluginData = listOf(PluginData(
                id = PluginData.Id("myPlugin"),
                pluginModuleRoot = ".",
                moduleExtensionSchemaName = SchemaName("com.example.CustomPluginSchema"),
                classTypes = listOf(
                    PluginData.ClassData(
                        name = SchemaName("com.example.CustomPluginSchema"),
                        properties = listOf(
                            PluginData.ClassData.Property(
                                name = "foo",
                                type = PluginData.Type.IntType(),
                            ),
                            PluginData.ClassData.Property(
                                name = "bar",
                                type = PluginData.Type.StringType(),
                            ),
                        )
                    )
                ),
            ))
        )
    )
    
    @Test
    fun `defaults for no value are correctly added`() = testRefineModule(
        "defaults-for-no-value",
        selectedContexts = platformCtxs("jvm"),
        withDefaults = true,
    )
    
    private fun platformCtxs(vararg values: String) = 
        values.map { PlatformCtx(it, null) }.toSet()
}