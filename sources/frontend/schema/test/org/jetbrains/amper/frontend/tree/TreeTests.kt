/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.contexts.PathCtx
import org.jetbrains.amper.frontend.contexts.PlatformCtx
import org.jetbrains.amper.frontend.helpers.FrontendTestCaseBase
import org.jetbrains.amper.frontend.helpers.diagnoseModuleRead
import org.jetbrains.amper.frontend.helpers.testModuleRead
import org.jetbrains.amper.frontend.helpers.testRefineModule
import org.jetbrains.amper.frontend.helpers.testRefineModuleWithTemplates
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.plugins.schema.model.SourceLocation
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.div

class TreeTests : FrontendTestCaseBase(Path(".") / "testResources" / "valueTree") {

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
    fun `unknown properties with misnomer`() {
        diagnoseModuleRead("misnomer-did-you-mean")
    }

    @Test
    fun `merge with templates`() = testRefineModuleWithTemplates(
        "with-templates",
        selectedContexts = { platformCtxs("jvm") + PathCtx(it, null) },
    )

    @Suppress("unused")
    class CustomPluginSchema : SchemaNode() {
        val foo: Int by value()
        val bar: String by value()
    }

    @Test
    fun `read module file with custom properties`() = testModuleRead(
        "with-custom-properties",
        types = SchemaTypingContext(
            pluginData = testPluginData(),
        )
    )

    @Test
    fun `read module file with custom properties diagnostics`() = diagnoseModuleRead(
        "with-custom-properties-diagnostics",
        types = SchemaTypingContext(
            pluginData = testPluginData(),
        )
    )

    private fun platformCtxs(vararg values: String) =
        values.map { PlatformCtx(it, null) }.toSet()

    private fun testPluginData(): List<PluginData> {
        val stubLocation = SourceLocation(Path("/"), -1..-1)
        return listOf(
            PluginData(
                id = PluginData.Id("myPlugin"),
                pluginSettingsSchemaName = PluginData.SchemaName("com.example", listOf("CustomPluginSchema")),
                declarations = PluginData.Declarations(
                    classes = listOf(
                        PluginData.ClassData(
                            name = PluginData.SchemaName("com.example", listOf("CustomPluginSchema")),
                            origin = stubLocation,
                            properties = listOf(
                                PluginData.ClassData.Property(
                                    name = "foo",
                                    type = PluginData.Type.IntType(),
                                    origin = stubLocation,
                                ),
                                PluginData.ClassData.Property(
                                    name = "bar",
                                    type = PluginData.Type.StringType(),
                                    origin = stubLocation,
                                ),
                            )
                        )
                    ),
                ),
            )
        )
    }
}