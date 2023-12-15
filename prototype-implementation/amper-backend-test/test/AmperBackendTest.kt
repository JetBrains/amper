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
    fun `do not call kotlinc again if sources were not changed`() {
        val projectContext = ProjectContext(
            projectRoot = AmperProjectRoot(testDataRoot.resolve("language-version")),
            projectTempRoot = AmperProjectTempRoot(tempRoot.resolve("projectTemp")),
            userCacheRoot = userCacheRoot,
            buildOutputRoot = AmperBuildOutputRoot(tempRoot.resolve("buildOutput")),
        )

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
        val projectContext = ProjectContext(
            projectRoot = AmperProjectRoot(testDataRoot.resolve("language-version")),
            projectTempRoot = AmperProjectTempRoot(tempRoot.resolve("projectTemp")),
            userCacheRoot = userCacheRoot,
            buildOutputRoot = AmperBuildOutputRoot(tempRoot.resolve("buildOutput")),
        )

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

    // Re-use user cache root for local runs to make testing faster
    // On CI (TeamCity) make it per-build (temp directory for build is cleaned after each build run)
    private val userCacheRoot: AmperUserCacheRoot = if (TeamCityHelper.isUnderTeamCity) {
        AmperUserCacheRoot(TeamCityHelper.tempDirectory.resolve("amperUserCacheRoot"))
    } else AmperUserCacheRoot(TestUtil.sharedTestCaches)

    private val testDataRoot: Path = TestUtil.prototypeImplementationRoot.resolve("amper-backend-test/testData/projects")
}