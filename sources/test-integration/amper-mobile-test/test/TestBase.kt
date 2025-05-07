/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.amper.processes.runProcessAndCaptureOutput
import org.jetbrains.amper.test.AmperCliWithWrapperTestBase
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.LocalAmperPublication
import org.jetbrains.amper.test.android.AndroidTools
import org.jetbrains.amper.test.gradle.runGradle
import org.junit.jupiter.api.AfterEach
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Provides common utility functions.
 */
open class TestBase : AmperCliWithWrapperTestBase() {
    protected val amperMobileTestsRoot = Dirs.amperSourcesRoot / "test-integration" / "amper-mobile-test"
    private val tempProjectsDir = amperMobileTestsRoot / "tempProjects"

    @AfterEach
    fun cleanup() {
        tempProjectsDir.deleteRecursively()
    }

    /**
     * Copies the project from the specified [projectSource] to a temporary projects directory.
     * Setup Amper in the copied project directory.
     *
     * @param localProjectsDirectory in-source projects directory for [ProjectSource.Local] projects.
     *
     * @return the path to root directory of the copied project. Its name is guaranteed to match [projectName] to avoid
     * issues with Gradle projects that don't specify a `rootProject.name` explicitly.
     */
    suspend fun copyProjectToTempDir(projectSource: ProjectSource, localProjectsDirectory: Path): Path {
        val sourceDir = when(projectSource) {
            is ProjectSource.Local -> {
                // Construct the local source directory path
                localProjectsDirectory.resolve(projectSource.name).also {
                    check(it.exists()) { "Project ${projectSource.name} is not found in $localProjectsDirectory" }
                }
            }
            is ProjectSource.RemoteRepository -> {
                val repoDir = createTempDirectory() / projectSource.cloneIntoDirName
                try {
                    gitClone(
                        repoUrl = projectSource.cloneUrl,
                        refLike = projectSource.refLikeToCheckout,
                        cloneDestination = repoDir,
                    )

                    println("Git clone successful. Temp directory: $repoDir")

                    // Verify if the repo has been cloned by checking the .git folder
                    val gitFolder = repoDir.resolve(".git")
                    if (!gitFolder.exists()) {
                        error("Git clone completed but .git folder is missing. Something went wrong.")
                    }
                    println("Git repository successfully cloned.")

                    val sourceDir = repoDir / projectSource.projectRelativePath

                    // Log the path where the project is expected
                    println("Project path being copied from: $sourceDir")

                    check(sourceDir.exists()) {
                        "Project directory does not exist in the cloned repository (${projectSource.cloneUrl} at) at $sourceDir"
                    }
                    sourceDir
                } catch (ex: IOException) {
                    throw RuntimeException("Failed to clone Git repository", ex)
                }
            }
        }.normalize()

        // Destination path for the copied project
        val destinationProjectPath = tempProjectsDir / sourceDir.name

        try {
            // Ensure the destination directory exists
            tempProjectsDir.createDirectories()

            // Copy the project files from source to destination
            println("Copying project from $sourceDir to $destinationProjectPath")
            sourceDir.copyToRecursively(target = destinationProjectPath, followLinks = false, overwrite = true)
            LocalAmperPublication.setupWrappersIn(destinationProjectPath)
        } catch (ex: IOException) {
            throw RuntimeException("Failed to copy files from $sourceDir to $destinationProjectPath", ex)
        }
        return destinationProjectPath
    }

    /**
     * Clones the Git repository from [repoUrl] (migrated-projects repository) into the specified [cloneDestination].
     */
    private suspend fun gitClone(repoUrl: String, refLike: String?, cloneDestination: Path) {
        runProcessAndCaptureOutput(
            command = listOf("git", "clone", repoUrl, cloneDestination.toAbsolutePath().toString()),
            redirectErrorStream = true,
        ).also { result ->
            if (result.exitCode != 0) {
                error("Git clone failed with exit code ${result.exitCode}. Output: ${result.stdout}")
            }
        }

        if (refLike != null) {
            runProcessAndCaptureOutput(
                command = listOf("git", "checkout", refLike),
                workingDir = cloneDestination.toAbsolutePath(),
                redirectErrorStream = true,
            ).also { result ->
                if (result.exitCode != 0) {
                    error("Git checkout to `$refLike` failed with exit code ${result.exitCode}. Output: ${result.stdout}")
                }
            }
            println("Successfully checked out `$refLike`.")
        }
    }

    /**
     * Configures Amper in the Gradle settings file for the specified [projectDir].
     *
     * @throws IllegalArgumentException if the settings file contains a relative `includeBuild` call without a required marker.
     */
    fun putAmperToGradleFile(projectDir: Path, runWithPluginClasspath: Boolean) {
        val gradleFile = projectDir / "settings.gradle.kts"
        require(gradleFile.exists()) { "file not found: $gradleFile" }

        if (runWithPluginClasspath) {
            val lines = gradleFile.readLines().filterNot { "<REMOVE_LINE_IF_RUN_WITH_PLUGIN_CLASSPATH>" in it }
            gradleFile.writeText(lines.joinToString("\n"))

            val gradleFileText = gradleFile.readText()
            val newText = gradleFileText.replace(
                "mavenCentral()",
                """
            mavenCentral()
            mavenLocal()
            maven("https://www.jetbrains.com/intellij-repository/releases")
            maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
            """.trimIndent()
            )
            if (!gradleFileText.contains("mavenLocal()")) {
                gradleFile.writeText(newText)
            }

            require(gradleFile.readText().contains("mavenLocal")) {
                "Gradle file must have 'mavenLocal' after replacement: $gradleFile"
            }

            val updatedText = gradleFile.readText().replace(
                "id(\"org.jetbrains.amper.settings.plugin\")",
                "id(\"org.jetbrains.amper.settings.plugin\") version(\"+\")"
            )
            if (!gradleFileText.contains("version(")) {
                gradleFile.writeText(updatedText)
            }

            require(gradleFile.readText().contains("version(")) {
                "Gradle file must have 'version(' after replacement: $gradleFile"
            }
        }

        if (gradleFile.readText().contains("includeBuild(\".\"")) {
            throw IllegalArgumentException("Example project ${projectDir.name} has a relative includeBuild() call, but it's run within Amper tests from a moved directory. Add a comment '<REMOVE_LINE_IF_RUN_WITH_PLUGIN_CLASSPATH>' on the same line if this included build is for Amper itself (will be removed if Amper is on the classpath).")
        }
    }

    /**
     * Assembles the target app in the specified [projectDir] using Gradle’s `assemble` task.
     */
    suspend fun assembleTargetApp(projectDir: Path, subprojectName: String? = null) {
        val task = if (subprojectName == null) "assemble" else ":$subprojectName:assemble"

        println("Executing 'gradle $task' in ${projectDir.name}")
        runGradle(
            projectDir = projectDir,
            args = listOf(task),
            cmdName = "gradle",
            testReporter = testReporter,
            additionalEnv = AndroidTools.getOrInstallForTests().environment(),
        )
        println("Finished 'gradle $task' in ${projectDir.name}")
    }

    protected fun amperExternalProject(
        name: String,
    ) = ProjectSource.RemoteRepository(
        cloneUrl = "ssh://git.jetbrains.team/amper/amper-external-projects.git",
        cloneIntoDirName = "amper-external-projects",
        projectRelativePath = Path(name),
    )

    /**
     * Where the test project is located and how to access it
     */
    sealed interface ProjectSource {
        /**
         * The project is located inside the Amper repository.
         */
        data class Local(
            /**
             * Local project directory name. The directory is located in a conventional place.
             */
            val name: String,
        ) : ProjectSource

        /**
         * The project needs to be cloned for the remote repository.
         */
        data class RemoteRepository(
            /**
             * url to clone from.
             */
            val cloneUrl: String,

            /**
             * Name of the directory to clone into.
             * May matter for the repositories with the project at their root because this becomes the project name.
             */
            val cloneIntoDirName: String,

            /**
             * An optional ref-like string (commit, branch) to checkout after cloning.
             * Nothing is explicitly checked out if `null`.
             */
            val refLikeToCheckout: String? = null,

            /**
             * A relative path to the project inside the cloned root.
             */
            val projectRelativePath: Path = Path("."),
        ) : ProjectSource {
            init {
                require(!projectRelativePath.isAbsolute) { "Expected a relative path, got $projectRelativePath" }
            }
        }
    }
}
