package org.jetbrains.deft.proto.frontend.fragments.yaml

import org.jetbrains.deft.proto.frontend.PotatoModuleProgrammaticSource
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

    @Test
    fun `only ios`() = testYamlParseFromFile("only_ios")

    @Test
    fun `kotlin fragment parts`() = testYamlParseFromFile("kotlin_fragment_parts")

    @Test
    fun `variants order`() = testYamlParseFromFile("variants_order")

    @Test
    fun `duplicate fragments`() = testYamlParseFromFile("duplicate_fragments")

    @Test
    fun `duplicate fragments with variants`() = testYamlParseFromFile("duplicate_fragments_with_variants")

    @Test
    fun `custom fragment should have refines`() = testYamlParseFromFile("custom_fragment_without_refines")

    @Test
    fun `custom fragment should be explicitly defined without variants`() = testYamlParseFromFile("custom_fragment_defined_only_with_variant")

    @Test
    fun `unknown variant`() = testYamlParseFromFile("unknown_variant")

    @Test
    fun `fragments can't have two variants from the same dimension`() = testYamlParseFromFile("two_variants_from_same_dimension")

    @Test
    fun `plus can't be used in variant name`() = testYamlParseFromFile("plus_in_variant_name")

    @Test
    fun `variant values should be unique`() = testYamlParseFromFile("duplicate_variant")

    @Test
    fun `artifact settings`() = testYamlParseFromFile("artifact_settings")

    @Test
    fun `empty fragment`() = testYamlParseFromFile("empty_fragment")

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