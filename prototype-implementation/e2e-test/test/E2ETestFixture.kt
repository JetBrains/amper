import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.assertTrue


open class E2ETestFixture(val pathToProjects: String) {
    @OptIn(ExperimentalPathApi::class)
    internal fun test(
        projectName: String,
        vararg buildArguments: String,
        expectOutputToHave: String,
        shouldSucceed: Boolean = true,
    ) {
        test(
            projectName,
            buildArguments = buildArguments,
            listOf(expectOutputToHave),
            shouldSucceed
        )
    }

    @OptIn(ExperimentalPathApi::class)
    internal fun test(
        projectName: String,
        vararg buildArguments: String,
        expectOutputToHave: Collection<String>,
        shouldSucceed: Boolean = true,
    ) {
        val tempDir = prepareTempDirWithProject(projectName)
        try {
            val runner = GradleRunner.create()
                .withPluginClasspath()
                .withProjectDir(tempDir.toFile())
                .withDebug(true)
                .withArguments(*buildArguments, "--stacktrace")
            val buildResult = if (shouldSucceed) runner.build() else runner.buildAndFail()
            val output = buildResult.output

            val missingStrings = expectOutputToHave.filter { !output.contains(it) }
            assertTrue(missingStrings.isEmpty(),
                "The following strings are not found in the build ouptut:\n" +
                        missingStrings.joinToString("\n") { "\t" + it } +
                        "\nOutput:\n$output")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @OptIn(ExperimentalPathApi::class)
    private fun prepareTempDirWithProject(projectName: String): Path {
        val implementationDir = Path.of("../../prototype-implementation").toAbsolutePath()
        val originalDir = Path.of("${pathToProjects}/$projectName")

        assertTrue(implementationDir.exists(), "Deft plugin project not found at $implementationDir")
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
                    id("org.jetbrains.deft.proto.settings.plugin")
                }
                """.trimIndent()
        )

        return tempDir
    }
}
