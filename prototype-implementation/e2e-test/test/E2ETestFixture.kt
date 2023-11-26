import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.assertTrue


open class E2ETestFixture(val pathToProjects: String) {

    @Suppress("unused") // JUnit5 extension.
    @field:RegisterExtension
    val daemonManagerExtension = GradleDaemonManager

    /**
     * Daemon, used to run this test.
     */
    lateinit var gradleRunner: GradleRunner

    @OptIn(ExperimentalPathApi::class)
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
        val tempDir = prepareTempDirWithProject(projectName)
        val newEnv = System.getenv().toMutableMap().apply { putAll(additionalEnv) }
        try {
            val runner = gradleRunner
                .withPluginClasspath()
                .withProjectDir(tempDir.toFile())
                .withEnvironment(newEnv)
//                .withDebug(true)
                .withArguments(*buildArguments, "--stacktrace")
            val buildResult = if (shouldSucceed) runner.build() else runner.buildAndFail()
            val output = buildResult.output

            val missingStrings = expectOutputToHave.filter { !output.contains(it) }
            assertTrue(missingStrings.isEmpty(),
                "The following strings are not found in the build ouptut:\n" +
                        missingStrings.joinToString("\n") { "\t" + it } +
                        "\nOutput:\n$output")

            if (checkForWarnings) output.checkForWarnings()

            additionalCheck(tempDir)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @OptIn(ExperimentalPathApi::class)
    private fun prepareTempDirWithProject(projectName: String): Path {
        val implementationDir = Path.of("../../prototype-implementation").toAbsolutePath()
        val originalDir = Path.of("${pathToProjects}/$projectName")

        assertTrue(implementationDir.exists(), "Amper plugin project not found at $implementationDir")
        assertTrue(originalDir.exists(), "Test project not found at $originalDir")

        // prepare data
        val tempDir = File.createTempFile("test-", "-$projectName").toPath()
        tempDir.deleteRecursively()

        val followLinks = false
        val ignore = setOf(".gradle", "build", "local.properties")
        originalDir.copyToRecursively(tempDir, followLinks = followLinks) { src, dst ->
            if (src.name in ignore) CopyActionResult.SKIP_SUBTREE
            else src.copyToIgnoringExistingDirectory(dst, followLinks = followLinks)
        }

        val gradleFile = tempDir.resolve("settings.gradle.kts")
        assertTrue(gradleFile.exists(), "file not found: $gradleFile")

        gradleFile.writeText(
            """
                pluginManagement {
                    repositories {
                        mavenCentral()
                        google()
                        gradlePluginPortal()
                    }
                }
    
                plugins {
                    id("org.jetbrains.amper.settings.plugin")
                }
                """.trimIndent()
        )

        return tempDir
    }
}
