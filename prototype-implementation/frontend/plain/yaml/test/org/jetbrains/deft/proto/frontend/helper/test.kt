package org.jetbrains.deft.proto.frontend.helper

import org.jetbrains.deft.proto.core.get
import org.jetbrains.deft.proto.core.getOrElse
import org.jetbrains.deft.proto.core.messages.*
import org.jetbrains.deft.proto.frontend.*
import org.junit.jupiter.api.assertThrows
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

context (BuildFileAware)
internal fun testParse(
    resourceName: String,
    osDetector: OsDetector = DefaultOsDetector(),
    init: TestDirectory.() -> Unit = { directory("src") }
) {
    val text = ParserKtTest::class.java.getResource("/$resourceName.yaml")?.readText()
        ?: fail("Resource not found")
    val parsed = Yaml().load<Settings>(text)
    doTestParse(resourceName, parsed, osDetector, init)
}

context (BuildFileAware)
internal fun testParseWithTemplates(
    resourceName: String,
    properties: Properties = Properties(),
    init: TestDirectory.() -> Unit = { directory("src") },
) {
    val path = Path(".")
        .toAbsolutePath()
        .resolve("test/resources/$resourceName.yaml")
    if (!path.exists()) fail("Resource not found: $path")

    with(TestProblemReporterContext) {
        val parsed = with(properties) {
            Yaml().parseAndPreprocess(path) {
                Path(".")
                    .toAbsolutePath()
                    .resolve("test/resources/$it")
            }
        }
        problemReporter.tearDown()

        doTestParse(resourceName, parsed.getOrElse { fail("Failed to parse: $path") }, init = init)
    }
}

context (BuildFileAware)
internal fun doTestParse(
    baseName: String,
    parsed: Settings,
    osDetector: OsDetector = DefaultOsDetector(),
    init: TestDirectory.() -> Unit = { directory("src") }
) {

    project(buildFile.parent.toFile()) { init() }

    // When
    val module = withBuildFile(buildFile.toAbsolutePath()) {
        parseModule(parsed, osDetector)
    }.get()

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
            .replace("{{ potDir }}", buildFile.parent.relativize(Path.of(potDir)).toString())
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

inline fun <reified T : Throwable> assertThrowsWithErrorMessage(expectedMessage: String, executable: () -> Unit): T {
    val e = assertThrows<T>(executable)
    assertEquals(expectedMessage, e.message)
    return e
}

private object TestProblemReporter : CollectingProblemReporter() {
    fun tearDown() {
        val errors = problems.filter { it.level == Level.Error }
        if (errors.isNotEmpty()) {
            fail(buildString {
                appendLine()
                errors.forEach { error ->
                    append(renderMessage(error))
                    appendLine()
                }
            }.also { problems.clear() })
        }
    }

    override fun doReportMessage(message: BuildProblem) {
        if (message.level == Level.Warning) {
            println("WARNING: " + renderMessage(message))
        }
    }
}

private object TestProblemReporterContext : ProblemReporterContext {
    override val problemReporter: TestProblemReporter = TestProblemReporter
}
