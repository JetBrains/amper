import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.trace.data.SpanData
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.diagnostics.getAttribute
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.tinylog.Level
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.test.assertTrue
import kotlin.test.fail

abstract class IntegrationTestBase {
    @TempDir
    private lateinit var tempDir: Path

    protected val tempRoot by lazy {
        // Always run tests in a directory with space, tests quoting in a lot of places
        tempDir.resolve("space test")
    }

    @RegisterExtension
    protected val logCollector = LogCollectorExtension()

    @RegisterExtension
    protected val stdoutCollector: StdStreamCollectorExtension = StdoutCollectorExtension()

    @RegisterExtension
    protected val stderrCollector: StdStreamCollectorExtension = StderrCollectorExtension()

    @RegisterExtension
    protected val openTelemetryCollector = OpenTelemetryCollectorExtension()

    private val kotlinCompilationSpanName = "kotlin-compilation"

    protected val kotlinJvmCompilerSpans: List<SpanData>
        get() = openTelemetryCollector.spans.filter { it.name == kotlinCompilationSpanName }

    private val javacSpanName = "javac"

    protected val javacSpans: List<SpanData>
        get() = openTelemetryCollector.spans.filter { it.name == javacSpanName }

    private val userCacheRoot: AmperUserCacheRoot = AmperUserCacheRoot(TestUtil.userCacheRoot)

    protected fun setupTestProject(testProjectPath: Path, copyToTemp: Boolean): ProjectContext {
        require(testProjectPath.exists()) { "Test project is missing at $testProjectPath" }

        val projectRoot = if (copyToTemp) testProjectPath.copyToTempRoot() else testProjectPath
        return ProjectContext(
            projectRoot = AmperProjectRoot(projectRoot),
            projectTempRoot = AmperProjectTempRoot(tempRoot.resolve("projectTemp")),
            userCacheRoot = userCacheRoot,
            buildOutputRoot = AmperBuildOutputRoot(tempRoot.resolve("buildOutput")),
        )
    }

    @OptIn(ExperimentalPathApi::class)
    private fun Path.copyToTempRoot(): Path = tempRoot.resolve(fileName).also { dir ->
        dir.createDirectories()
        copyToRecursively(target = dir, followLinks = true, overwrite = false)
    }

    protected fun assertInfoLogStartsWith(msgPrefix: String) = assertLogStartsWith(msgPrefix, level = Level.INFO)

    protected fun assertLogStartsWith(msgPrefix: String, level: Level) {
        assertTrue("Log message with level=$level and starting with '$msgPrefix' was not found") {
            logCollector.entries.any { it.level == level && it.message.startsWith(msgPrefix) }
        }
    }

    protected fun assertInfoLogContains(msgPrefix: String) = assertLogContains(msgPrefix, level = Level.INFO)

    protected fun assertLogContains(text: String, level: Level) {
        assertTrue("Log message with level=$level and containing '$text' was not found") {
            logCollector.entries.any { it.level == level && text in it.message }
        }
    }

    protected fun assertStdoutContains(text: String) {
        assertTrue("No line in stdout contains the text '$text'") {
            stdoutCollector.capturedText().lineSequence().any { text in it }
        }
    }

    protected fun SpanData.assertKotlinCompilerArgument(argumentName: String, argumentValue: String) {
        require(name == kotlinCompilationSpanName) {
            "Cannot assert Kotlin compiler arguments on span '$name' (should be '$kotlinCompilationSpanName')"
        }
        val args = getAttribute(AttributeKey.stringArrayKey("compiler-args"))
        val pair = argumentName to argumentValue

        assertTrue("Compiler argument '$argumentName $argumentValue' is missing. Actual args: $args") {
            args.zipWithNext().contains(pair)
        }
    }

    protected fun SpanData.assertJavaCompilerArgument(argumentName: String, argumentValue: String) {
        require(name == javacSpanName) {
            "Cannot assert Java compiler arguments on span '$name' (should be '$javacSpanName')"
        }
        val args = getAttribute(AttributeKey.stringArrayKey("args"))
        val pair = argumentName to argumentValue

        assertTrue("Compiler argument '$argumentName $argumentValue' is missing. Actual args: $args") {
            args.zipWithNext().contains(pair)
        }
    }
}
