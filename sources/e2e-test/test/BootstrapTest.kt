/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.apache.commons.io.output.TeeOutputStream
import org.gradle.tooling.GradleConnector
import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.CollectingProblemReporter
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.test.TestUtil
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.yaml.snakeyaml.Yaml
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertTrue

class BootstrapTest {

    @TempDir
    lateinit var projectPath: Path

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `amper could build itself using version from sources`() {
        // given
        val commonTemplatePath = TestUtil.amperSourcesRoot.resolve("common.module-template.yaml")

        val commonTemplate = commonTemplatePath.inputStream().use { Yaml().load<Map<String, Any>>(it) }
        val yamlSettings = commonTemplate.getValue("settings") as Map<String, Any>
        val yamlPublishing = yamlSettings.getValue("publishing") as Map<String, String>
        val version = yamlPublishing.getValue("version")

        println("############ gradle-integration version: $version")

        copyDirectory(Path("../.."), projectPath)
        val settingsKts = projectPath.resolve("settings.gradle.kts")

        settingsKts.readText()
            .mustReplace(Regex.fromLiteral("// mavenLocal()"), "mavenLocal()")
            .mustReplace(
                Regex(Regex.escape("id(\"org.jetbrains.amper.settings.plugin\") version \"") +
                            "[\\w\\-.]+\""),
                "id(\"org.jetbrains.amper.settings.plugin\") version \"$version\""
            )
            .let { settingsKts.writeText(it) }

        // when
        val gradleHome = TestUtil.sharedTestCaches.resolve("gradleHome")
            .also { it.createDirectories() }
        val projectConnector = GradleConnector.newConnector()
            .useGradleUserHomeDir(gradleHome.toFile())
            .forProjectDirectory(projectPath.toFile())
            .connect()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        projectConnector.newBuild()
            .withArguments(
                //                "-Dorg.gradle.jvmargs=-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005",
                // --no-build-cache to actually build stuff instead of getting it from cache since cache is shared between runs
                "assemble", "--stacktrace", "--no-build-cache", "-PinBootstrapMode=true")
            .setStandardOutput(TeeOutputStream(System.out, stdout))
            .setStandardError(TeeOutputStream(System.err, stderr))
            .run()
        val output = (stdout.toByteArray().decodeToString() + "\n" + stderr.toByteArray().decodeToString()).replace("\r", "")

        // then
        assertTrue { output.contains("BUILD SUCCESSFUL") }
    }

    private fun String.mustReplace(regex: Regex, replacement: String): String {
        val s = replace(regex, replacement)
        if (this == s) {
            error("No replacements were made while replacing '${regex.pattern}' with '$replacement':\n" + this)
        }
        return s
    }
}

// TODO Replace by copyRecursively.
private fun copyDirectory(source: Path, target: Path) {
    Files.walk(source).use { paths ->
        paths.forEach { sourcePath ->
            if (sourcePath.any { it.pathString == "build" || it.pathString == ".gradle" }) return@forEach
            // could be present to attach a java agent to the process. Not relevant for this test
            if (sourcePath.name.startsWith(".attach_pid")) return@forEach

            val targetPath = target.resolve(source.relativize(sourcePath))
            if (Files.isDirectory(sourcePath)) {
                if (!Files.exists(targetPath)) {
                    Files.createDirectory(targetPath)
                }
            } else {
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

class TestProblemReporter : CollectingProblemReporter() {
    override fun doReportMessage(message: BuildProblem) {}

    fun getErrors(): List<BuildProblem> = problems.filter { it.level == Level.Error || it.level == Level.Fatal }
}

class TestProblemReporterContext : ProblemReporterContext {
    override val problemReporter: TestProblemReporter = TestProblemReporter()
}
