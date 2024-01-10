import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.trace.data.SpanData
import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.ProjectContext
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.tinylog.Level
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Ignore
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
        assertEquals(0, AmperBackend.run(projectContext, listOf(":jvm-kotlin-test-smoke:testJvm")))

        val testLauncherSpan = openTelemetryCollector.spans.single { it.name == "junit-platform-console-standalone" }
        val stdout = testLauncherSpan.attributes[AttributeKey.stringKey("stdout")]!!

        // not captured by default...
        assertTrue(stdout.contains("Hello from test method"), stdout)

        assertTrue(stdout.contains("[         1 tests successful      ]"), stdout)
        assertTrue(stdout.contains("[         0 tests failed          ]"), stdout)

        val xmlReport = projectContext.buildOutputRoot.path.resolve("tasks/_jvm-kotlin-test-smoke_testJvm/reports/TEST-junit-jupiter.xml")
            .readText()

        assertTrue(xmlReport.contains("<testcase name=\"smoke()\" classname=\"apkg.ATest\""), xmlReport)
    }

    @Ignore
    @Test
    fun `get jvm resource from dependency`() {
        val projectContext = getProjectContext("jvm-resources")
        assertEquals(0, AmperBackend.run(projectContext, listOf(":two:runJvm")))

        assertInfoLogContains(
            "Process exited with exit code 0\n" +
                    "STDOUT:\n" +
                    "String from resources: Stuff From Resources"
        )
    }

    @Test
    fun `do not call kotlinc again if sources were not changed`() {
        val projectContext = getProjectContext("language-version")

        assertEquals(0, AmperBackend.run(projectContext, listOf(":language-version:runJvm")))
        assertInfoLogContains(
            "Process exited with exit code 0\n" +
                    "STDOUT:\n" +
                    "Hello, world!"
        )
        assertEquals(1, kotlincSpans.size)

        openTelemetryCollector.reset()
        log.reset()

        assertEquals(0, AmperBackend.run(projectContext, listOf(":language-version:runJvm")))
        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Hello, world!"
        assertInfoLogContains(find)
        assertEquals(0, kotlincSpans.size)
    }

    @Test
    fun `kotlinc span`() {
        val projectContext = getProjectContext("language-version")
        val rc = AmperBackend.run(projectContext, listOf(":language-version:runJvm"))
        assertEquals(0, rc)

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Hello, world!"
        assertInfoLogContains(find)

        val kotlinc = kotlincSpans.singleOrNull() ?: fail("No kotlinc spans")
        kotlinc.assertKotlincArguments("-language-version", "1.9")

        val kotlincExitCode = kotlinc.attributes[AttributeKey.longKey("exit-code")]!!
        assertEquals(0, kotlincExitCode)

        val kotlincStdErr = kotlinc.attributes[AttributeKey.stringKey("stderr")]!!
        val substring = "main.kt:1:10: warning: parameter 'args' is never used"
        if (!kotlincStdErr.contains(substring)) {
            fail("kotlinc stderr output must contain '$substring': $kotlincStdErr")
        }

        val kotlincStdOut = kotlinc.attributes[AttributeKey.stringKey("stdout")]!!
        assertEquals("", kotlincStdOut)

        val kotlincAmperModule = kotlinc.attributes[AttributeKey.stringKey("amper-module")]!!
        assertEquals("language-version", kotlincAmperModule)
    }

    @Test
    fun `mixed java kotlin`() {
        val projectContext = getProjectContext("java-kotlin-mixed")
        val rc = AmperBackend.run(projectContext, listOf(":java-kotlin-mixed:runJvm"))
        assertEquals(0, rc)

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Output: <XYZ>"
        assertInfoLogContains(find)
    }

    @Test
    fun `simple multiplatform cli on jvm`() {
        val projectContext = getProjectContext("simple-multiplatform-cli")
        val rc = AmperBackend.run(projectContext, listOf(":jvm-cli:runJvm"))
        assertEquals(0, rc)

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Hello Multiplatform CLI: JVM World"
        assertInfoLogContains(find)
    }

    @Test
    @MacOnly
    fun `simple multiplatform cli on mac`() {
        val projectContext = getProjectContext("simple-multiplatform-cli")
        val rc = AmperBackend.run(projectContext, listOf(":macos-cli:runMacosArm64"))
        assertEquals(0, rc)

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Hello Multiplatform CLI: Mac World"
        assertInfoLogContains(find)
    }

    @Test
    @MacOnly
    fun `simple multiplatform cli test on mac`() {
        val projectContext = getProjectContext("simple-multiplatform-cli")
        val rc = AmperBackend.run(projectContext, listOf(":shared:testMacosArm64"))
        assertEquals(0, rc)

        val testLauncherSpan = openTelemetryCollector.spans.single { it.name == "native-test" }
        val stdout = testLauncherSpan.attributes[AttributeKey.stringKey("stdout")]!!

        assertTrue(stdout.contains("[       OK ] WorldTest.doTest"), stdout)
        assertTrue(stdout.contains("[  PASSED  ] 1 tests"), stdout)
    }

    @Test
    @LinuxOnly
    fun `simple multiplatform cli on linux`() {
        val projectContext = getProjectContext("simple-multiplatform-cli")
        val rc = AmperBackend.run(projectContext, listOf(":linux-cli:runLinuxX64"))
        assertEquals(0, rc)

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "Hello Multiplatform CLI: Linux World"
        assertInfoLogContains(find)
    }

    private fun getProjectContext(testProjectName: String): ProjectContext {
        val projectContext = ProjectContext(
            projectRoot = AmperProjectRoot(testDataRoot.resolve(testProjectName)),
            projectTempRoot = AmperProjectTempRoot(tempRoot.resolve("projectTemp")),
            userCacheRoot = userCacheRoot,
            buildOutputRoot = AmperBuildOutputRoot(tempRoot.resolve("buildOutput")),
        )

        return projectContext
    }

    private fun assertInfoLogContains(startsWith: String) {
        assertTrue("Log message starting with '$startsWith' was not found") {
            log.entries.any { it.level.ordinal >= Level.INFO.ordinal && it.message.startsWith(startsWith) }
        }
    }

    private val tempRoot by lazy {
        // Always run tests in a directory with space, tests quoting in a lot of places
        tempDir.resolve("space test")
    }

    private val kotlincSpans: List<SpanData>
        get() = openTelemetryCollector.spans.filter { it.name == "kotlinc" }

    private fun SpanData.assertKotlincArguments(argumentName: String, argumentValue: String) {
        val args = attributes[AttributeKey.stringArrayKey("args")]!!
        val pair = argumentName to argumentValue

        // we can afford it!
        if (!args.zip(args.drop(1)).contains(pair)) {
            fail("command line arguments '$argumentName $argumentValue' must be in arguments: $args")
        }
    }

    private val userCacheRoot: AmperUserCacheRoot = AmperUserCacheRoot(TestUtil.userCacheRoot)

    private val testDataRoot: Path = TestUtil.prototypeImplementationRoot.resolve("amper-backend-test/testData/projects")
}
