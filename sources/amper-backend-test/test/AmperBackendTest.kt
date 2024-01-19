
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.trace.data.SpanData
import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.diagnostics.getAttribute
import org.jetbrains.amper.engine.TaskName
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.tinylog.Level
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class AmperBackendTest {
    @TempDir
    private lateinit var tempDir: Path

    @RegisterExtension
    private val log = LogCollectorExtension()

    @RegisterExtension
    val openTelemetryCollector = OpenTelemetryCollectorExtension()

    @Test
    fun `jvm kotlin-test smoke test`() {
        val projectContext = getProjectContext("jvm-kotlin-test-smoke")
        AmperBackend(projectContext).runTask(TaskName(":jvm-kotlin-test-smoke:testJvm"))

        val testLauncherSpan = openTelemetryCollector.spans.single { it.name == "junit-platform-console-standalone" }
        val stdout = testLauncherSpan.getAttribute(AttributeKey.stringKey("stdout"))

        // not captured by default...
        assertTrue(stdout.contains("Hello from test method"), stdout)

        assertTrue(stdout.contains("[         1 tests successful      ]"), stdout)
        assertTrue(stdout.contains("[         0 tests failed          ]"), stdout)

        val xmlReport = projectContext.buildOutputRoot.path.resolve("tasks/_jvm-kotlin-test-smoke_testJvm/reports/TEST-junit-jupiter.xml")
            .readText()

        assertTrue(xmlReport.contains("<testcase name=\"smoke()\" classname=\"apkg.ATest\""), xmlReport)
    }

    @Test
    fun `get jvm resource from dependency`() {
        val projectContext = getProjectContext("jvm-resources")
        AmperBackend(projectContext).runTask(TaskName(":two:runJvm"))

        assertInfoLogStartsWith(
            "Process exited with exit code 0\n" +
                    "STDOUT:\n" +
                    "String from resources: Stuff From Resources"
        )
    }

    @Test
    fun `do not call kotlinc again if sources were not changed`() {
        val projectContext = getProjectContext("language-version")

        AmperBackend(projectContext).runTask(TaskName(":language-version:runJvm"))
        assertInfoLogStartsWith(
            "Process exited with exit code 0\n" +
                    "STDOUT:\n" +
                    "Hello, world!"
        )
        assertEquals(1, kotlinJvmCompilerSpans.size)

        openTelemetryCollector.reset()
        log.reset()

        AmperBackend(projectContext).runTask(TaskName(":language-version:runJvm"))
        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Hello, world!"
        assertInfoLogStartsWith(find)
        assertEquals(0, kotlinJvmCompilerSpans.size)
    }

    @Test
    fun `kotlin compiler span`() {
        val projectContext = getProjectContext("language-version")
        AmperBackend(projectContext).runTask(TaskName(":language-version:runJvm"))

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Hello, world!"
        assertInfoLogStartsWith(find)

        val compilationSpan = kotlinJvmCompilerSpans.singleOrNull() ?: fail("No kotlin compilation span (or more than 1)")
        compilationSpan.assertKotlinCompilerArgument("-language-version", "1.9")

        assertLogContains(text = "main.kt:1:10 Parameter 'args' is never used", level = Level.WARN)

        val amperModuleAttr = compilationSpan.getAttribute(AttributeKey.stringKey("amper-module"))
        assertEquals("language-version", amperModuleAttr)
    }

    @Test
    fun `mixed java kotlin`() {
        val projectContext = getProjectContext("java-kotlin-mixed")
        AmperBackend(projectContext).runTask(TaskName(":java-kotlin-mixed:runJvm"))

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Output: <XYZ>"
        assertInfoLogStartsWith(find)
    }

    @Test
    fun `simple multiplatform cli on jvm`() {
        val projectContext = getProjectContext("simple-multiplatform-cli")
        AmperBackend(projectContext).runTask(TaskName(":jvm-cli:runJvm"))

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Hello Multiplatform CLI: JVM World"
        assertInfoLogStartsWith(find)
    }

    @Test
    @MacOnly
    fun `simple multiplatform cli on mac`() {
        val projectContext = getProjectContext("simple-multiplatform-cli")
        AmperBackend(projectContext).runTask(TaskName(":macos-cli:runMacosArm64"))

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Hello Multiplatform CLI: Mac World"
        assertInfoLogStartsWith(find)
    }

    @Test
    @MacOnly
    fun `simple multiplatform cli test on mac`() {
        val projectContext = getProjectContext("simple-multiplatform-cli")
        AmperBackend(projectContext).runTask(TaskName(":shared:testMacosArm64"))

        val testLauncherSpan = openTelemetryCollector.spans.single { it.name == "native-test" }
        val stdout = testLauncherSpan.getAttribute(AttributeKey.stringKey("stdout"))

        assertTrue(stdout.contains("[       OK ] WorldTest.doTest"), stdout)
        assertTrue(stdout.contains("[  PASSED  ] 1 tests"), stdout)
    }

    @Test
    @LinuxOnly
    fun `simple multiplatform cli on linux`() {
        val projectContext = getProjectContext("simple-multiplatform-cli")
        AmperBackend(projectContext).runTask(TaskName(":linux-cli:runLinuxX64"))

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Hello Multiplatform CLI: Linux World"
        assertInfoLogStartsWith(msgPrefix = find)
    }

    @OptIn(ExperimentalPathApi::class)
    private fun getProjectContext(testProjectName: String, copy: Boolean = false): ProjectContext {
        val testDataProjectRoot = testDataRoot.resolve(testProjectName)
            .also { check(it.exists()) { "Test project is missing at $it" } }
        val projectRoot = if (copy) {
            val dir = tempDir.resolve(testProjectName)
            dir.createDirectories()
            testDataProjectRoot.copyToRecursively(dir, followLinks = true, overwrite = false)
            dir
        } else testDataProjectRoot
        val projectContext = ProjectContext(
            projectRoot = AmperProjectRoot(projectRoot),
            projectTempRoot = AmperProjectTempRoot(tempRoot.resolve("projectTemp")),
            userCacheRoot = userCacheRoot,
            buildOutputRoot = AmperBuildOutputRoot(tempRoot.resolve("buildOutput")),
        )

        return projectContext
    }

    private fun assertInfoLogStartsWith(msgPrefix: String) = assertLogStartsWith(msgPrefix, minLevel = Level.INFO)

    private fun assertLogStartsWith(msgPrefix: String, minLevel: Level) {
        assertTrue("Log message with level>=$minLevel and starting with '$msgPrefix' was not found") {
            log.entries.any { it.level >= minLevel && it.message.startsWith(msgPrefix) }
        }
    }

    private fun assertLogContains(text: String, level: Level) {
        assertTrue("Log message with level=$level and containing '$text' was not found") {
            log.entries.any { it.level == level && text in it.message }
        }
    }

    private val tempRoot by lazy {
        // Always run tests in a directory with space, tests quoting in a lot of places
        tempDir.resolve("space test")
    }

    private val kotlinCompilationSpanName = "kotlin-compilation"

    private val kotlinJvmCompilerSpans: List<SpanData>
        get() {
            return openTelemetryCollector.spans.filter { it.name == kotlinCompilationSpanName }
        }

    private fun SpanData.getKotlinCompilerArguments(): List<String> {
        require(name == kotlinCompilationSpanName)
        return getAttribute(AttributeKey.stringArrayKey("compiler-args"))
    }

    private fun SpanData.assertKotlinCompilerArgument(argumentName: String, argumentValue: String) {
        val args = getKotlinCompilerArguments()
        val pair = argumentName to argumentValue

        // we can afford it!
        if (!args.zip(args.drop(1)).contains(pair)) {
            fail("command line arguments '$argumentName $argumentValue' must be in arguments: $args")
        }
    }

    private val userCacheRoot: AmperUserCacheRoot = AmperUserCacheRoot(TestUtil.userCacheRoot)

    private val testDataRoot: Path = TestUtil.amperSourcesRoot.resolve("amper-backend-test/testData/projects")
}
