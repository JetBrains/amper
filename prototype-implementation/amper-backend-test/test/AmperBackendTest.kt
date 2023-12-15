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
    fun `language level is passed to kotlin compiler`() {
        // Always run tests in a directory with space, tests quoting in a lot of places
        val tempRoot = tempDir.resolve("space test")

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
        assertTrue("Log message starting with '$find' was not found") {
            log.entries.any { it.message.startsWith(find) }
        }

        val kotlinc = openTelemetryCollector.spans.singleOrNull { it.name == "kotlinc" } ?: fail("No kotlinc spans")
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