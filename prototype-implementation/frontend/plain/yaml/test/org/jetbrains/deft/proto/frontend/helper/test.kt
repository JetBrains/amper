package org.jetbrains.deft.proto.frontend.helper

import org.jetbrains.deft.proto.frontend.*
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.exists
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
    val path = Path(".")
        .toAbsolutePath()
        .resolve("test/resources/$resourceName.yaml")
    if (!path.exists()) fail("Resource not found: $path")

    val parsed = with(properties) {
        Yaml().parseAndPreprocess(
            path
        ) {
            Path(".")
                .toAbsolutePath()
                .resolve("test/resources/$it")
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

    val resourceFile = File(".").absoluteFile
        .resolve("test/resources/$expectedResourceName")
        .takeIf { it.exists() } ?: fail("Resource $expectedResourceName not found")

    val buildDir = (module.source as PotatoModuleFileSource).buildDir.normalize().toString()
    val userReadableName = module.userReadableName
    val potDir = resourceFile.absoluteFile.normalize().parent.toString()
    val testProcessDir = File(".").absoluteFile.normalize().toString()

    if (!fastReplaceExpected_USE_CAREFULLY) {
        // This is actual check.
        val resourceFileText = resourceFile.readText()
        val expected = resourceFileText
            .replace("{{ userReadableName }}", userReadableName)
            .replace("{{ buildDir }}", buildDir)
            .replace("{{ potDir }}", potDir)
            .replace("{{ testProcessDir }}", testProcessDir)
        assertEquals(expected, actual)
    } else {
        // This is fast replace mode.
        val toReplace = actual
            .replace(userReadableName, "{{ userReadableName }}")
            .replace(buildDir, "{{ buildDir }}")
            .replace(potDir, "{{ potDir }}")
            .replace(testProcessDir, "{{ testProcessDir }}")
        resourceFile.writeText(toReplace)
    }
}