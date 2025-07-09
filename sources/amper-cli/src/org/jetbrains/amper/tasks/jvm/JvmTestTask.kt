/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.downloader.Downloader
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.diagnostics.DeadLockMonitor
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.engine.TestTask
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.jdk.provisioning.JdkDownloader
import org.jetbrains.amper.processes.PrintToTerminalProcessOutputListener
import org.jetbrains.amper.processes.runJava
import org.jetbrains.amper.tasks.EmptyTaskResult
import org.jetbrains.amper.tasks.JvmTestRunSettings
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.TestResultsFormat
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.use
import org.jetbrains.amper.test.FilterMode
import org.jetbrains.amper.test.TestFilter
import org.jetbrains.amper.test.wildcardsToRegex
import org.jetbrains.amper.util.BuildType
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.outputStream
import kotlin.io.path.pathString

class JvmTestTask(
    private val userCacheRoot: AmperUserCacheRoot,
    private val taskOutputRoot: TaskOutputRoot,
    private val tempRoot: AmperProjectTempRoot,
    private val projectRoot: AmperProjectRoot,
    private val buildOutputRoot: AmperBuildOutputRoot,
    private val terminal: Terminal,
    private val runSettings: JvmTestRunSettings,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    override val module: AmperModule,
    override val taskName: TaskName,
    override val platform: Platform = Platform.JVM,
    override val buildType: BuildType? = null,
): TestTask {

    init {
        require(platform == Platform.JVM || platform == Platform.ANDROID) {
            "Illegal platform for JvmTestTask: $platform"
        }
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        // https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.1/junit-platform-console-standalone-1.10.1.jar
        val junitConsoleUrl = Downloader.getUriForMavenArtifact(
            mavenRepository = "https://repo1.maven.org/maven2",
            groupId = "org.junit.platform",
            artifactId = "junit-platform-console-standalone",
            packaging = "jar",
            version = UsedVersions.junitPlatform,
        ).toString()

        // test task depends on compile test task
        val compileTask = dependenciesResult.filterIsInstance<JvmCompileTask.Result>().singleOrNull()
            ?: error("${JvmCompileTask::class.simpleName} result is not found in dependencies")
        if (compileTask.classesOutputRoot.listDirectoryEntries().isEmpty()) {
            logger.warn("No test classes, skipping test execution for module '${module.userReadableName}'")
            return EmptyTaskResult
        }

        // test task depends on test jvm classpath task
        val jvmRuntimeClasspathTask = dependenciesResult.filterIsInstance<JvmRuntimeClasspathTask.Result>().singleOrNull()
            ?: error("${JvmRuntimeClasspathTask::class.simpleName} result is not found in dependencies")

        val userTestRuntimeClasspath = jvmRuntimeClasspathTask.jvmRuntimeClasspath

        val junitConsole = Downloader.downloadFileToCacheLocation(junitConsoleUrl, userCacheRoot)

        // TODO use maven instead of packing this in the distribution?
        val amperJUnitListenersJars = extractJUnitListenersClasspath()

        val jdk = JdkDownloader.getJdk(userCacheRoot)

        cleanDirectory(taskOutputRoot.path)

        val reportsDir = buildOutputRoot.path / "reports" / module.userReadableName / platform.schemaValue
        cleanDirectory(reportsDir)

        val junitArgs = buildList {
            add("--disable-banner")

            // TODO I certainly want to have it here by default (too many real life errors when tests are skipped for a some reason)
            //  but probably there should be an option to disable it
            add("--fail-if-no-tests")
            add("--reports-dir=${reportsDir}")

            val filterArguments = runSettings.testFilters.map { it.toJUnitArgument() }
            addAll(filterArguments)
            if (filterArguments.isEmpty() ||
                filterArguments.any { it.startsWith("--include") || it.startsWith("--exclude") }) {
                add("--scan-class-path=${compileTask.classesOutputRoot}")
            }
            add("--details=summary") // disable default console tree output, just print the summary
        }

        val jvmArgs = buildList {
            add("-ea")

            if (runSettings.testResultsFormat == TestResultsFormat.Pretty) {
                add("-Dorg.jetbrains.amper.junit.listener.console.enabled=true")
                // We don't use inherited IO when starting the test launcher process, so the Mordant Terminal library
                // inside the test launcher cannot detect the supported features of the current console.
                // This is why we currently just "transfer" the detected features via CLI arguments.
                // Using ProcessBuilder.inheritIO() would make any auto-detection in the test launcher work, but then the
                // test launcher output doesn't mix well with our task progress renderer.
                add("-Dorg.jetbrains.amper.junit.listener.console.ansiLevel=${terminal.terminalInfo.ansiLevel}")
                add("-Dorg.jetbrains.amper.junit.listener.console.ansiHyperlinks=${terminal.terminalInfo.ansiHyperLinks}")
            }
            if (runSettings.testResultsFormat == TestResultsFormat.TeamCity) {
                add("-Dorg.jetbrains.amper.junit.listener.teamcity.enabled=true")
            }

            val jvmTestSettings = module.leafFragments.single { it.platform == platform && it.isTest }.settings.jvm.test
            addAll(jvmTestSettings.systemProperties.map { (k, v) -> "-D${k.value}=${v.value}" })
            addAll(jvmTestSettings.freeJvmArgs)

            addAll(runSettings.userJvmArgs)
        }

        // We pass both the user classpath and the "infra" classpath (JUnit itself and our listeners) together
        // instead of using the separate --class-path option of the JUnit Console Launcher itself.
        // This is intentional, to work around this JUnit issue: https://github.com/junit-team/junit5/issues/4469.
        // In short, the separate --class-path option of the launcher was mostly meant as a convenience, but in
        // some cases it is harmful. It is loaded in a separate class loader that is closed at the end of the tests,
        // but before the test JVM shuts down. If the user code uses shutdown hooks (e.g., in testcontainers), they
        // would not be able to load user classes anymore because of this.
        val testJvmClasspath = listOf(junitConsole) + amperJUnitListenersJars + userTestRuntimeClasspath

        // TODO should be customizable?
        // There is no way of knowing what the working dir should be for generated/unresolved test modules,
        // the project root is a somewhat safe choice.
        val workingDirectory = module.source.moduleDir ?: projectRoot.path

        return spanBuilder("junit-platform-console-standalone")
            .setAttribute("junit-platform-console-standalone", junitConsole.pathString)
            .setAttribute("working-dir", workingDirectory.pathString)
            .setListAttribute("tests-classpath", userTestRuntimeClasspath.map { it.pathString })
            .setListAttribute("jvm-args", jvmArgs)
            .setListAttribute("junit-args", junitArgs)
            .use { span ->
                logger.info("Testing module '${module.userReadableName}' for platform '${platform.pretty}'...")

                DeadLockMonitor.disable()

                val result = jdk.runJava(
                    workingDir = workingDirectory,
                    mainClass = "org.junit.platform.console.ConsoleLauncher",
                    jvmArgs = jvmArgs,
                    classpath = testJvmClasspath,
                    programArgs = listOf("execute") + junitArgs,
                    tempRoot = tempRoot,
                    outputListener = PrintToTerminalProcessOutputListener(terminal),
                )

                // TODO exit code from junit launcher should be carefully become some kind of exit code for entire Amper run
                //  + one more interesting case: if we reported some failed tests to TeamCity, exit code of Amper should be 0,
                //  since the build will be failed anyway and it'll just have one more useless build failure about exit code
                if (result.exitCode != 0) {
                    val meaning = if (result.exitCode == 2) " (no tests were discovered)" else ""
                    userReadableError("JVM tests failed for module '${module.userReadableName}' with exit code ${result.exitCode}$meaning (see errors above)")
                }
                EmptyTaskResult
            }
    }

    private suspend fun extractJUnitListenersClasspath(): List<Path> {
        val classpathList = javaClass.getResource("/junit-listeners/classpath.txt")?.readText()
            ?: error("JUnit listeners classpath is not in the Amper distribution")
        val result = executeOnChangedInputs.execute(
            id = "extract-junit-listeners-classpath",
            configuration = mapOf("junit-listeners-classpath" to classpathList),
            inputs = emptyList(),
        ) {
            val launcherDir = tempRoot.path.resolve("amper-junit-listeners").createDirectories()
            cleanDirectory(launcherDir) // we don't want to keep old dependencies that were potentially removed
            val jarNames = classpathList.lines()
            jarNames.forEach { jarName ->
                val jarResource = javaClass.getResourceAsStream("/junit-listeners/lib/$jarName")
                    ?: error("Cannot find JUnit listeners jar in resources: $jarName")
                jarResource.use {
                    launcherDir.resolve(jarName).outputStream().use { out ->
                        it.copyTo(out)
                    }
                }
            }
            ExecuteOnChangedInputs.ExecutionResult(outputs = jarNames.map { launcherDir.resolve(it) })
        }
        return result.outputs
    }

    private fun TestFilter.toJUnitArgument(): String =
        when (this) {
            is TestFilter.SpecificTestInclude -> toJUnitSelectArgument()
            is TestFilter.SuitePattern -> when (mode) {
                FilterMode.Exclude -> "--exclude-classname=${pattern.slashToDollar().wildcardsToRegex()}"
                FilterMode.Include -> when {
                    "*" in pattern || "?" in pattern -> "--include-classname=${pattern.slashToDollar().wildcardsToRegex()}"
                    // Note: using --select=nested-class:com.example.Enclosing/Nested as specified in the docs doesn't
                    // work, but using the plain --select-class with a $ sign works fine...
                    else -> "--select-class=${pattern.slashToDollar()}"
                }
            }
        }

    private fun TestFilter.SpecificTestInclude.toJUnitSelectArgument(): String {
        // Note: using --select=nested-method:com.example.Enclosing/Nested.myTest as specified in the docs doesn't
        // work, but using the plain --select-method with a $ sign to separate the nested class works fine...
        val nestedClassSuffix = if (nestedClassName != null) "\$$nestedClassName" else ""
        val paramsList = if (paramTypes != null) "(${paramTypes.joinToString(",")})" else ""
        return "--select-method=$suiteFqn$nestedClassSuffix#$testName$paramsList"
    }

    private fun String.slashToDollar() = replace('/', '$')
}
