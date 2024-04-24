/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import org.apache.commons.io.output.TeeOutputStream
import org.gradle.tooling.GradleConnector
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.core.AmperBuild
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.core.extract.extractZip
import org.jetbrains.amper.downloader.Downloader
import org.jetbrains.amper.downloader.suspendingRetryWithExponentialBackOff
import org.jetbrains.amper.test.TestUtil
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.io.path.CopyActionResult
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.deleteExisting
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeLines
import kotlin.io.path.writeText
import kotlin.test.assertTrue
import kotlin.test.fail


open class E2ETestFixture(val pathToProjects: String, val runWithPluginClasspath: Boolean = true) {

    @Suppress("unused") // JUnit5 extension.
    @field:RegisterExtension
    val daemonManagerExtension = GradleDaemonManager

    /**
     * Daemon, used to run this test.
     */
    lateinit var gradleRunner: GradleConnector

    internal fun test(
        projectName: String,
        vararg buildArguments: String,
        expectOutputToHave: String,
        shouldSucceed: Boolean = true,
        checkForWarnings: Boolean = true,
        additionalEnv: Map<String, String> = emptyMap(),
        additionalCheck: (Path) -> Unit = {},
    ) {
        test(
            projectName,
            buildArguments = buildArguments,
            listOf(expectOutputToHave),
            shouldSucceed,
            checkForWarnings,
            additionalEnv,
            additionalCheck,
        )
    }

    @OptIn(ExperimentalPathApi::class)
    internal fun test(
        projectName: String,
        vararg buildArguments: String,
        expectOutputToHave: Collection<String>,
        shouldSucceed: Boolean = true,
        checkForWarnings: Boolean = true,
        additionalEnv: Map<String, String> = emptyMap(),
        additionalCheck: (Path) -> Unit = {},
    ) {
        val tempDir = prepareTempDirWithProject(projectName, runWithPluginClasspath)
        val newEnv = System.getenv().toMutableMap().apply { putAll(additionalEnv) }

        newEnv["ANDROID_HOME"] = androidHome.pathString
        val runner = gradleRunner
        val projectConnector = runner
            .forProjectDirectory(tempDir.toFile())
            .connect()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        try {
            projectConnector.newBuild()
                .setEnvironmentVariables(newEnv)
                // --no-build-cache: do not get a build result from shared Gradle cache
                .withArguments(*buildArguments, "--stacktrace", "--no-build-cache")
                .setStandardOutput(TeeOutputStream(System.out, stdout))
                .setStandardError(TeeOutputStream(System.err, stderr))
//            .addJvmArguments("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5006")
                .run()
        } catch (t: Throwable) {
            if (shouldSucceed) {
                throw t
            } else {
                // skip, the error will be checked by expectOutputToHave
            }
        }
        val output = (stdout.toByteArray().decodeToString() + "\n" + stderr.toByteArray().decodeToString()).replace("\r", "")

        val missingStrings = expectOutputToHave.filter { !output.contains(it) }
        assertTrue(missingStrings.isEmpty(),
            "The following strings are not found in the build output:\n" +
                    missingStrings.joinToString("\n") { "\t" + it } +
                    "\nOutput:\n$output")

        if (checkForWarnings) output.checkForWarnings()

        additionalCheck(tempDir)
    }

    @OptIn(ExperimentalPathApi::class)
    private fun prepareTempDirWithProject(projectName: String, runWithPluginClasspath: Boolean): Path {
        val implementationDir = Path.of("../../sources").toAbsolutePath()
        val originalDir = Path.of("${pathToProjects}/$projectName")

        assertTrue(implementationDir.exists(), "Amper plugin project not found at $implementationDir")
        assertTrue(originalDir.exists(), "Test project not found at $originalDir")

        // prepare data
        val tempDir = Files.createTempFile(TestUtil.tempDir, "test-", "-$projectName")
        tempDir.deleteExisting()
        GradleDaemonManager.deleteFileOrDirectoryOnExit(tempDir)

        val followLinks = false
        val ignore = setOf(".gradle", "build", "caches")
        originalDir.copyToRecursively(tempDir, followLinks = followLinks) { src, dst ->
            if (src.name in ignore) CopyActionResult.SKIP_SUBTREE
            else src.copyToIgnoringExistingDirectory(dst, followLinks = followLinks)
        }

        val gradleFile = tempDir.resolve("settings.gradle.kts")
        assertTrue(gradleFile.exists(), "file not found: $gradleFile")

        if (runWithPluginClasspath) {
            // we don't want to replace the entire settings.gradle.kts because its contents may be part of the test
            gradleFile.writeLines(gradleFile.readLines().filter { "<REMOVE_LINE_IF_RUN_WITH_PLUGIN_CLASSPATH>" !in it })

            check(!gradleFile.readText().contains("mavenLocal", ignoreCase = true)) {
                "Gradle files in testData are not supposed to reference mavenLocal by default: " +
                        "$gradleFile\n${gradleFile.readText().prependIndent("> ")}"
            }

            // Add actual plugin to classpath, it's published to mavenLocal before running tests
            gradleFile.writeText(gradleFile.readText().replace(
                "mavenCentral()",
                """
                    mavenCentral()
                    mavenLocal()
                    maven("https://www.jetbrains.com/intellij-repository/releases")
                    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
                """.trimIndent()
            ))
            check(gradleFile.readText().contains("mavenLocal")) {
                "Gradle file must have 'mavenLocal' after replacement: $gradleFile\n${gradleFile.readText().prependIndent("> ")}"
            }

            // Add Amper plugin version
            gradleFile.writeText(gradleFile.readText().replace(
                Regex("id\\(\"org.jetbrains.amper.settings.plugin\"\\)[. ]version\\(\".+\"\\)", RegexOption.MULTILINE),
                "id(\"org.jetbrains.amper.settings.plugin\")"
            ))

            gradleFile.writeText(gradleFile.readText().replace(
                "id(\"org.jetbrains.amper.settings.plugin\")",
                "id(\"org.jetbrains.amper.settings.plugin\") version(\"${AmperBuild.BuildNumber}\")"
            ))
            check(gradleFile.readText().contains("version(")) {
                "Gradle file must have 'version(' after replacement: $gradleFile\n${gradleFile.readText().prependIndent("> ")}"
            }
        }

        // These errors can be tricky to figure out
        if ("includeBuild(\"." in gradleFile.readText()) {
            fail("Example project $projectName has a relative includeBuild() call, but it's run within Amper tests " +
                    "from a moved directory. Add a comment '<REMOVE_LINE_IF_RUN_WITH_PLUGIN_CLASSPATH>' on the same " +
                    "line if this included build is for Amper itself (will be removed if Amper is on the classpath).")
        }
        return tempDir
    }

    companion object {
        private fun acceptAndroidLicense(androidHome: Path, name: String, hash: String) {
            val licenseFile = androidHome / "licenses" / name
            Files.createDirectories(licenseFile.parent)

            if (!licenseFile.isRegularFile() || licenseFile.readText() != hash) {
                licenseFile.writeText(hash)
            }
        }

        private suspend fun installToolIfMissing(home: Path, pack: String, userCacheRoot: AmperUserCacheRoot) {
            val java = JdkDownloader.getJdk(userCacheRoot).javaExecutable

            val sdkManagerJar = home / "cmdline-tools" / "lib" / "sdkmanager-classpath.jar"

            val sourceProperties = home.resolve(pack.replace(';', '/')) / "source.properties"
            if (sourceProperties.exists()) {
                return
            }

            println("Installing '$pack' into '$home'")

            val cmd = listOf(
                java.pathString,
                "-cp",
                sdkManagerJar.pathString,
                "com.android.sdklib.tool.sdkmanager.SdkManagerCli",
                "--sdk_root=$home",
                pack
            )
            println("  running ${cmd.joinToString("|")}")

            runInterruptible {
                val pb = ProcessBuilder()
                    .command(*cmd.toTypedArray())
                    .inheritIO()
                    .start()
                val rc = pb.waitFor()
                if (rc != 0) {
                    error("Android SdkManager failed with exit code $rc while executing: ${cmd.joinToString("|")}")
                }
            }

            if (!sourceProperties.exists()) {
                error("source.properties file '$sourceProperties' is missing after installing package '$pack'")
            }
        }

        // from https://github.com/thyrlian/AndroidSDK/blob/master/android-sdk/license_accepter.sh
        private val licensesToAccept: List<Pair<String, String>> = listOf(
            "android-googletv-license" to "601085b94cd77f0b54ff86406957099ebe79c4d6",
            "android-sdk-license" to "8933bad161af4178b1185d1a37fbf41ea5269c55",
            "android-sdk-license" to "d56f5187479451eabf01fb78af6dfcb131a6481e",
            "android-sdk-license" to "24333f8a63b6825ea9c5514f83c2829b004d1fee",
            "android-sdk-preview-license" to "84831b9409646a918e30573bab4c9c91346d8abd",
            "android-sdk-preview-license" to "504667f4c0de7af1a06de9f4b1727b84351f2910",
            "google-gdk-license" to "33b6a2b64607f11b759f320ef9dff4ae5c47d97a",
            "intel-android-extra-license" to "d975f751698a77b662f1254ddbeed3901e976f5a",
        )

        private val toolsToInstall = listOf(
            "platform-tools",
            "platforms;android-31",
            "platforms;android-33",
            "platforms;android-34",
            "build-tools;33.0.0",
            "build-tools;33.0.1",
            "build-tools;34.0.0",
        )

        private val androidHome: Path by lazy {
            runBlocking(Dispatchers.IO) {
                val fakeUserCacheRoot = AmperUserCacheRoot(TestUtil.sharedTestCaches)
                val fakeBuildOutputRoot = AmperBuildOutputRoot(TestUtil.sharedTestCaches)

                val commandLineTools = suspendingRetryWithExponentialBackOff {
                    Downloader.downloadFileToCacheLocation(
                        "https://dl.google.com/android/repository/commandlinetools-win-10406996_latest.zip",
                        fakeUserCacheRoot,
                    )
                }

                val configuration = mapOf(
                    "cmdToolsRevision" to getCmdToolsPkgRevision(commandLineTools),
                    "licenses" to licensesToAccept.joinToString(" ") { "${it.first}-${it.second}" },
                    "packages" to toolsToInstall.joinToString(" "),
                )

                val root = ExecuteOnChangedInputs(fakeBuildOutputRoot).execute(
                    "android-sdk",
                    configuration,
                    inputs = emptyList()
                ) {
                    val root = fakeUserCacheRoot.path / "android-sdk"
                    cleanDirectory(root)

                    extractZip(commandLineTools, root / "cmdline-tools", true)

                    acceptAndroidLicense(root, "android-googletv-license", "601085b94cd77f0b54ff86406957099ebe79c4d6")
                    acceptAndroidLicense(root, "android-sdk-license", "8933bad161af4178b1185d1a37fbf41ea5269c55")
                    acceptAndroidLicense(root, "android-sdk-license", "d56f5187479451eabf01fb78af6dfcb131a6481e")
                    acceptAndroidLicense(root, "android-sdk-license", "24333f8a63b6825ea9c5514f83c2829b004d1fee")
                    acceptAndroidLicense(
                        root,
                        "android-sdk-preview-license",
                        "84831b9409646a918e30573bab4c9c91346d8abd"
                    )
                    acceptAndroidLicense(
                        root,
                        "android-sdk-preview-license",
                        "504667f4c0de7af1a06de9f4b1727b84351f2910"
                    )
                    acceptAndroidLicense(root, "google-gdk-license", "33b6a2b64607f11b759f320ef9dff4ae5c47d97a")
                    acceptAndroidLicense(
                        root,
                        "intel-android-extra-license",
                        "d975f751698a77b662f1254ddbeed3901e976f5a"
                    )

                    for (tool in toolsToInstall) {
                        suspendingRetryWithExponentialBackOff(backOffLimitMs = TimeUnit.MINUTES.toMillis(1)) {
                            installToolIfMissing(root, tool, fakeUserCacheRoot)
                        }
                    }

                    return@execute ExecuteOnChangedInputs.ExecutionResult(listOf(root))
                }.outputs.single()

                return@runBlocking root
            }
        }

        private fun getCmdToolsPkgRevision(commandLineToolsZip: Path): String {
            val cmdToolsSourceProperties = ZipFile(commandLineToolsZip.toFile()).use { zip ->
                zip.getInputStream(zip.getEntry("cmdline-tools/source.properties")!!).readAllBytes()
            }
            val props = Properties().also { it.load(ByteArrayInputStream(cmdToolsSourceProperties)) }
            val cmdToolsPkgRevision: String? = props.getProperty("Pkg.Revision")
            check(!cmdToolsPkgRevision.isNullOrBlank()) {
                "Unable to get Pkg.Revision from $commandLineToolsZip"
            }

            return cmdToolsPkgRevision
        }
    }
}
