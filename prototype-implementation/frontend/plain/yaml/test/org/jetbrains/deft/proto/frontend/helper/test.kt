package org.jetbrains.deft.proto.frontend.helper

import org.jetbrains.deft.proto.frontend.ParserKtTest
import org.jetbrains.deft.proto.frontend.Settings
import org.jetbrains.deft.proto.frontend.parseModule
import org.jetbrains.deft.proto.frontend.withBuildFile
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.file.Path
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

    project(parent.toFile()) { init() }

    // When
    val module = withBuildFile(toAbsolutePath()) {
        parseModule(parsed)
    }

    // Then
    val expectedResourceName = "$resourceName.result.txt"
    val actual = module.prettyPrint()
    if (!fastReplaceExpected_USE_CAREFULLY) {
        val expectedResource = ParserKtTest::class.java.getResource("/$expectedResourceName")
        val resultText = expectedResource?.readText()
            ?: fail("Resource not found")
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