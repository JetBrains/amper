package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.schema.NativeSettings
import org.jetbrains.amper.frontend.schema.RootSchema
import org.jetbrains.amper.frontend.tree.appendDefaultValues
import org.jetbrains.amper.frontend.tree.discoverCinteropDefs
import org.jetbrains.amper.frontend.tree.reading.readTree
import org.jetbrains.amper.frontend.tree.refineTree
import org.jetbrains.amper.frontend.util.assertIs
import org.junit.Test

class CinteropDiscoveryTest : BasePlatformTestCase() {

    private fun runTest(
        moduleYamlContent: String,
        setup: (FrontendPathResolver) -> Unit,
        assertions: (NativeSettings) -> Unit
    ) {
        val pathResolver = FrontendPathResolver(project)
        setup(pathResolver)
        val moduleFile = myFixture.tempDirFixture.createFile("my-module/module.yaml", moduleYamlContent)

        val buildCtx = BuildCtx(pathResolver, project)
        val tree = with(buildCtx) {
            readTree(moduleFile, schema.types.getType<RootSchema>())
                .appendDefaultValues()
                .discoverCinteropDefs()
        }
        val refinedTree = tree.refineTree(setOf("linuxX64"), buildCtx.schema.contexts)
        val root = buildCtx.createSchemaNode<RootSchema>(refinedTree)

        assertNotNull(root)
        val nativeSettings = root!!.product.fragments.single().settings.assertIs<NativeSettings>()
        assertions(nativeSettings)
    }

    @Test
    fun `cinterop defs are discovered automatically`() {
        runTest(
            moduleYamlContent = """
                product:
                  type: lib
                  platforms: [linuxX64]
                settings@linuxX64:
                  native: {}
            """.trimIndent(),
            setup = {
                myFixture.tempDirFixture.findOrCreateDir("my-module/resources/cinterop")
                myFixture.tempDirFixture.createFile("my-module/resources/cinterop/mydef.def")
            },
            assertions = { nativeSettings ->
                val cinterop = nativeSettings.cinterop
                assertNotNull(cinterop)
                assertEquals(1, cinterop.defs.size)
                assertEquals("resources/cinterop/mydef.def", cinterop.defs.single().value)
            }
        )
    }

    @Test
    fun `discovered and explicit defs are merged and deduplicated`() {
        runTest(
            moduleYamlContent = """
                product:
                  type: lib
                  platforms: [linuxX64]
                settings@linuxX64:
                  native:
                    cinterop:
                      defs:
                        - resources/cinterop/discovered.def # Duplicate
                        - explicit.def
            """.trimIndent(),
            setup = {
                myFixture.tempDirFixture.findOrCreateDir("my-module/resources/cinterop")
                myFixture.tempDirFixture.createFile("my-module/resources/cinterop/discovered.def")
            },
            assertions = { nativeSettings ->
                val cinterop = nativeSettings.cinterop
                assertNotNull(cinterop)
                assertEquals(2, cinterop.defs.size)
                assertTrue(cinterop.defs.any { it.value == "resources/cinterop/discovered.def" })
                assertTrue(cinterop.defs.any { it.value == "explicit.def" })
            }
        )
    }
}