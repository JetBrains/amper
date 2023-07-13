package org.jetbrains.deft.proto.frontend.helper

import org.jetbrains.deft.proto.frontend.*
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.file.Path
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.fail


/**
 * Use this flag to replace expected result fast and compare them using VCS.
 */
@Deprecated("This deprecation is set just to be sure this flag will be used carefully")
private const val fastReplaceExpected_USE_CAREFULLY = false

context (Path)
internal fun testParse(resourceName: String, init: TestDirectory.() -> Unit = { directory("src") }) {
    val text = ParserKtTest::class.java.getResource("/$resourceName.yaml")?.readText()
        ?: fail("Resource not found")
    val parsed = Yaml().load<Settings>(text)
    doTestParse(resourceName, parsed)
}

context (Path)
internal fun testParseWithTemplates(resourceName: String, properties: Properties = Properties()) {
    val stream = ParserKtTest::class.java.getResource("/$resourceName.yaml")?.openStream()
        ?: fail("Resource not found")
    val parsed = with(properties) {
        Yaml().parseAndPreprocess(
            stream
        ) {
            File(".").absoluteFile.resolve("test/resources/$it").inputStream()
        }
    }
    doTestParse(resourceName, parsed)
}

context (Path)
internal fun doTestParse(
    baseName: String,
    parsed: Settings,
    init: TestDirectory.() -> Unit = { directory("src") }
) {
    project(parent.toFile()) { init() }

    // When
    val module = withBuildFile(toAbsolutePath()) {
        parseModule(parsed)
    }

    // Then
    val expectedResourceName = "$baseName.result.txt"
    val actual = module.prettyPrint()
    if (!fastReplaceExpected_USE_CAREFULLY) {
        val expectedResource = ParserKtTest::class.java.getResource("/$expectedResourceName")
        val resultText = expectedResource?.readText()
            ?: fail("Resource ${expectedResourceName} not found")
        val expected = resultText.replace("{{ userReadableName }}", module.userReadableName)
        assertEquals(expected, actual)
    } else {
        val toReplace = actual.replace(module.userReadableName, "{{ userReadableName }}")
        val resourceFile = File(".").absoluteFile.resolve("test/resources/$expectedResourceName")
        if (resourceFile.exists()) resourceFile.writeText(toReplace)
        else fail(
            "Cannot replace ${resourceFile.absolutePath} contents. It does not exists.  " +
                    "See [fastReplaceExpected_USE_CAREFULLY] flag for details."
        )
    }
}