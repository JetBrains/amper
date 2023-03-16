package org.jetbrains.deft.proto.frontend.fragments.yaml

import org.jetbrains.deft.proto.frontend.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

class PotatoParserTest {
    @Test
    fun `empty yaml`() = testYamlParseFromFile("empty")

    @Test
    fun `no type`() = testYamlParseFromFile("no_type")

    @Test
    fun `no platforms`() = testYamlParseFromFile("no_platforms")

    @Test
    fun `unsupported platforms`() = testYamlParseFromFile("unsupported_platforms")

    @Test
    fun `simple app`() = testYamlParseFromFile("simple_app")

    @Test
    fun `simple lib`() = testYamlParseFromFile("simple_lib")

    @Test
    fun `mpp app`() = testYamlParseFromFile("mpp_app")

    @Test
    fun `mpp lib`() = testYamlParseFromFile("mpp_lib")

    @Test
    fun `custom fragment`() = testYamlParseFromFile("custom_fragment")

    @Test
    fun `fragment cycle`() = testYamlParseFromFile("fragment_cycle")

    private fun testYamlParseFromFile(testFile: String) {
        val resource = PotatoParserTest::class.java.getResource("${testFile}.yaml")
        checkNotNull(resource)
        val yamlText = resource.readText()
        val missingGold = "Add GOLD or Exception to the test"
        val gold = yamlText
            .substringAfter("### GOLD\n", missingGold)
            .lines()
            .joinToString("\n") { it.substringAfter("# ") }
        val exception = yamlText
            .substringAfter("### Exception", missingGold)
        when {
            gold != missingGold -> {
                val potatoModule = parsePotato(yamlText, testFile)
                assertEquals(testFile, potatoModule.userReadableName)
                assertEquals(PotatoModuleProgrammaticSource, potatoModule.source)
                assertEquals(gold, prettyPrint(potatoModule))
            }
            exception != missingGold -> assertFailsWith<ParsingException>("Should fail with an exception") { parsePotato(yamlText, testFile) }
            else -> fail("Add GOLD or exception to the test")
        }
    }
}