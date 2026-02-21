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
        setup: (FrontendPathResolver) -> Unit = {},
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
    fun `cinterop module is discovered automatically`() {
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
                val module = cinterop.modules["mydef"]
                assertNotNull(module)
                assertEquals("resources/cinterop/mydef.def", module!!.defFile)
            }
        )
    }

    @Test
    fun `discovered and explicit modules are merged`() {
        runTest(
            moduleYamlContent = """
                product:
                  type: lib
                  platforms: [linuxX64]
                settings@linuxX64:
                  native:
                    cinterop:
                      discovered:
                        linkerOpts: [ "src/c/discovered.c" ]
                      explicit:
                        defFile: "explicit.def"
            """.trimIndent(),
            setup = {
                myFixture.tempDirFixture.findOrCreateDir("my-module/resources/cinterop")
                myFixture.tempDirFixture.createFile("my-module/resources/cinterop/discovered.def")
            },
            assertions = { nativeSettings ->
                val cinterop = nativeSettings.cinterop
                assertNotNull(cinterop)
                assertEquals(2, cinterop.modules.size)

                val discoveredModule = cinterop.modules["discovered"]
                assertNotNull(discoveredModule)
                assertEquals("resources/cinterop/discovered.def", discoveredModule!!.defFile)
                assertEquals(listOf("src/c/discovered.c"), discoveredModule.linkerOpts)

                val explicitModule = cinterop.modules["explicit"]
                assertNotNull(explicitModule)
                assertEquals("explicit.def", explicitModule!!.defFile)
            }
        )
    }

    @Test
    fun `non-conventional module is configured correctly`() {
        runTest(
            moduleYamlContent = """
                product:
                  type: lib
                  platforms: [linuxX64]
                settings@linuxX64:
                  native:
                    cinterop:
                      custom:
                        defFile: "custom/path/custom.def"
                        packageName: "com.example.custom"
                        compilerOpts: [ "-I/custom/include" ]
                        linkerOpts: [ "custom/path/custom.c" ]
            """.trimIndent(),
            assertions = { nativeSettings ->
                val cinterop = nativeSettings.cinterop
                assertNotNull(cinterop)
                val module = cinterop.modules["custom"]
                assertNotNull(module)
                assertEquals("custom/path/custom.def", module!!.defFile)
                assertEquals("com.example.custom", module.packageName)
                assertEquals(listOf("-I/custom/include"), module.compilerOpts)
                assertEquals(listOf("custom/path/custom.c"), module.linkerOpts)
            }
        )
    }
}