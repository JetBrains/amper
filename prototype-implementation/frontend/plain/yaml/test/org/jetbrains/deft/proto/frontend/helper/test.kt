package org.jetbrains.deft.proto.frontend.helper

import com.intellij.rt.execution.junit5.FileComparisonFailedError
import org.jetbrains.deft.proto.core.get
import org.jetbrains.deft.proto.core.getOrNull
import org.jetbrains.deft.proto.core.messages.*
import org.jetbrains.deft.proto.core.system.DefaultSystemInfo
import org.jetbrains.deft.proto.core.system.SystemInfo
import org.jetbrains.deft.proto.frontend.*
import org.jetbrains.deft.proto.frontend.nodes.YamlNode
import org.jetbrains.deft.proto.frontend.nodes.toYamlNode
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText
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
    systemInfo: SystemInfo = DefaultSystemInfo,
    init: TestDirectory.() -> Unit = { directory("src") },
) {
    val testData = getTestDataResource("$resourceName.yaml")
    val text = testData.readText().removeDiagnosticsAnnotations()
    val parsed = Yaml().compose(text.reader()).toYamlNode(buildFile) as? YamlNode.Mapping
        ?: fail("Failed to parse: $resourceName.yaml")
    with(TestProblemReporterContext()) {
        doTestParse(resourceName, parsed, systemInfo, init)
        checkDiagnostics(testData, text, buildFile.parent, problemReporter.getErrors())
    }
}

internal fun getTestDataResource(testDataFileName: String): File {
    val file = File(".").absoluteFile
        .resolve("testResources/$testDataFileName")
    if (!file.exists()) fail("Resource $testDataFileName not found. Looked at ${file.canonicalPath}")
    return file
}


context (BuildFileAware)
internal fun testParseWithTemplates(
    resourceName: String,
    properties: Properties = Properties(),
    init: TestDirectory.() -> Unit = { directory("src") },
) {
    val testData = getTestDataResource("$resourceName.yaml")
    val testDataText = testData.readText().removeDiagnosticsAnnotations()
    // TODO(dsavvinov): hack to let parseAndPreprocess read the file without diagnostic annotations.
    //   can be removed when 'Reader' to parseAndPreprocess is added
    val testDataFileWithCleanedText = buildFile.parent.resolve("cleaned-$resourceName.yaml").also {
        it.writeText(testDataText)
    }

    with(TestProblemReporterContext()) {
        val parsed = with(properties) {
            Yaml().parseAndPreprocess(testDataFileWithCleanedText) {
                Path(".")
                    .toAbsolutePath()
                    .resolve("testResources/$it")
            }
        }

        doTestParse(resourceName, parsed.get(), init = init)
        checkDiagnostics(testData, testDataText, buildFile.parent, problemReporter.getErrors())
    }
}

context (BuildFileAware, ProblemReporterContext)
internal fun doTestParse(
    baseName: String,
    parsed: YamlNode.Mapping,
    sustemInfo: SystemInfo = DefaultSystemInfo,
    init: TestDirectory.() -> Unit = { directory("src") },
) {
    project(buildFile.parent.toFile()) { init() }

    // When
    val module = with(ParsingContext(parsed)) {
        parseModule(sustemInfo)
    }.getOrNull() ?: return

    // Then
    val expectedResourceName = "$baseName.result.txt"
    val actual = module.prettyPrint()

    val resourceFile = getTestDataResource(expectedResourceName)

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

private class TestProblemReporter : CollectingProblemReporter() {
    override fun doReportMessage(message: BuildProblem) {}

    fun getErrors(): List<BuildProblem> = problems.filter { it.level == Level.Error }

}

private class TestProblemReporterContext : ProblemReporterContext {
    override val problemReporter: TestProblemReporter = TestProblemReporter()
}

fun assertFileContentEquals(expectedContent: String, actualContent: String, originalFile: File) {
    if (expectedContent != actualContent) {
        throw FileComparisonFailedError("Comparison failed", expectedContent, actualContent, originalFile.absolutePath)
    }
}
