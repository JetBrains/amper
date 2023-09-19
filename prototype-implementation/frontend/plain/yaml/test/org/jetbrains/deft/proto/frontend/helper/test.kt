package org.jetbrains.deft.proto.frontend.helper

import org.jetbrains.deft.proto.core.get
import org.jetbrains.deft.proto.core.getOrElse
import org.jetbrains.deft.proto.core.messages.*
import org.jetbrains.deft.proto.frontend.*
import org.jetbrains.deft.proto.frontend.nodes.YamlNode
import org.jetbrains.deft.proto.frontend.nodes.toYamlNode
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
    checkErrors: ((problems: List<BuildProblem>) -> Unit)? = null,
    init: TestDirectory.() -> Unit = { directory("src") },
) {
    val text = ParserKtTest::class.java.getResource("/$resourceName.yaml")?.readText()
        ?: fail("Resource not found")
    val parsed = Yaml().compose(text.reader()).toYamlNode(buildFile) as? YamlNode.Mapping
        ?: fail("Failed to parse: $resourceName.yaml")
    with(TestProblemReporterContext) {
        val parseException = runCatching {
            doTestParse(resourceName, parsed, shouldFail = checkErrors != null, osDetector, init)
        }
        val checkErrorException = runCatching {
            if (checkErrors != null) {
                problemReporter.errorsChecked()
                checkErrors(problemReporter.getErrors())
            }
        }
        problemReporter.tearDown()
        parseException.exceptionOrNull()?.let { throw it }
        checkErrorException.exceptionOrNull()?.let { throw it }
    }
}

context (BuildFileAware)
internal fun testParseWithTemplates(
    resourceName: String,
    properties: Properties = Properties(),
    checkErrors: ((problems: List<BuildProblem>) -> Unit)? = null,
    init: TestDirectory.() -> Unit = { directory("src") },
) {
    val path = Path(".")
        .toAbsolutePath()
        .resolve("testResources/$resourceName.yaml")
    if (!path.exists()) fail("Resource not found: $path")

    with(TestProblemReporterContext) {
        val parsed = with(properties) {
            Yaml().parseAndPreprocess(path) {
                Path(".")
                    .toAbsolutePath()
                    .resolve("testResources/$it")
            }
        }
        val parseException = runCatching {
            doTestParse(resourceName, parsed.get(), shouldFail = checkErrors != null, init = init)
        }
        val checkErrorException = runCatching {
            if (checkErrors != null) {
                problemReporter.errorsChecked()
                checkErrors(problemReporter.getErrors())
            }
        }

        problemReporter.tearDown()
        parseException.exceptionOrNull()?.let { throw it }
        checkErrorException.exceptionOrNull()?.let { throw it }
    }
}

context (BuildFileAware, ProblemReporterContext)
internal fun doTestParse(
    baseName: String,
    parsed: YamlNode.Mapping,
    shouldFail: Boolean = false,
    osDetector: OsDetector = DefaultOsDetector(),
    init: TestDirectory.() -> Unit = { directory("src") },
) {
    project(buildFile.parent.toFile()) { init() }

    // When
    val module = withBuildFile(buildFile.toAbsolutePath()) {
        with(ParsingContext(parsed)) {
            parseModule(osDetector)
        }
    }.getOrElse {
        if (shouldFail) return else {
            fail("Failed to parse: $baseName.yaml")
        }
    }

    // Then
    val expectedResourceName = "$baseName.result.txt"
    val actual = module.prettyPrint()

    val resourceFile = File(".").absoluteFile
        .resolve("testResources/$expectedResourceName")
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

fun List<BuildProblem>.assertHasSingleProblem(block: BuildProblem.() -> Unit) {
    assertEquals(1, size)
    first().block()
}

private object TestProblemReporter : CollectingProblemReporter() {
    private var errorsChecked: Boolean = false

    fun tearDown() {
        val errors = problems.filter { it.level == Level.Error }
        if (errors.isNotEmpty()) {
            try {
                if (!errorsChecked) {
                    fail(buildString {
                        append("There are unchecked errors in test")
                        appendLine()
                        errors.forEach { error ->
                            append(renderMessage(error))
                            appendLine()
                        }
                    })
                }
            } finally {
                problems.clear()
                errorsChecked = false
            }
        }
    }

    override fun doReportMessage(message: BuildProblem) {
        if (message.level == Level.Warning) {
            println("WARNING: " + renderMessage(message))
        }
    }

    fun getErrors(): List<BuildProblem> = problems.filter { it.level == Level.Error }

    fun errorsChecked() {
        errorsChecked = true
    }
}

private object TestProblemReporterContext : ProblemReporterContext {
    override val problemReporter: TestProblemReporter = TestProblemReporter
}
