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
import java.nio.file.StandardOpenOption
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream
import kotlin.io.path.pathString
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

        val core = TestUtil.amperSourcesRoot.resolve("core/build/libs/core-jvm-$version.jar")
        val coreIntellij = TestUtil.amperSourcesRoot.resolve("core-intellij/build/libs/core-intellij-jvm-$version.jar")
        val gradleIntegration = TestUtil.amperSourcesRoot.resolve("gradle-integration/build/libs/gradle-integration-jvm-$version.jar")

        println("############ gradle-integration version: $version")
        println("############ gradle-integration libraries: ${TestUtil.amperSourcesRoot.resolve("gradle-integration/build/libs").toFile().list()?.toSet()}")

        val frontendApi = TestUtil.amperSourcesRoot.resolve("frontend-api/build/libs/frontend-api-jvm-$version.jar")
        val tomlPsi = TestUtil.amperSourcesRoot.resolve("frontend/plain/toml-psi/build/libs/toml-psi-jvm-$version.jar")
        val yamlPsi = TestUtil.amperSourcesRoot.resolve("frontend/plain/yaml-psi/build/libs/yaml-psi-jvm-$version.jar")
        val amperPsi = TestUtil.amperSourcesRoot.resolve("frontend/plain/amper-psi/build/libs/amper-psi-jvm-$version.jar")
        val parserStub = TestUtil.amperSourcesRoot.resolve("frontend/plain/parserutil-stub/build/libs/parserutil-stub-jvm-$version.jar")
        val schemaFrontend = TestUtil.amperSourcesRoot.resolve("frontend/schema/build/libs/schema-jvm-$version.jar")

        val intellijVersion = "233.13763.11"
        val xcodeModelSquashedVersion = "241.14980"
        // TODO: rewrite to include build approach
        val settingsContent = """
buildscript {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
        maven("https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases")
        maven("https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    }

    dependencies {
        classpath("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:1.9.20")
        classpath("org.jetbrains.kotlin.android:org.jetbrains.kotlin.android.gradle.plugin:1.9.20")
        classpath("com.android.library:com.android.library.gradle.plugin:8.1.0")
        classpath("org.jetbrains.kotlin:kotlin-serialization:1.9.20")
        classpath("com.jetbrains.intellij.platform:analysis-impl:$intellijVersion")
        classpath("com.jetbrains.intellij.platform:core:$intellijVersion")
        classpath("com.jetbrains.intellij.platform:core-impl:$intellijVersion")
        classpath("com.jetbrains.intellij.platform:core-ui:$intellijVersion")
        classpath("com.jetbrains.intellij.platform:ide-core:$intellijVersion")
        classpath("com.jetbrains.intellij.platform:util:$intellijVersion")
        classpath("com.jetbrains.intellij.platform:util-base:$intellijVersion")
        classpath("com.jetbrains.intellij.platform:util-ui:$intellijVersion")
        classpath("com.jetbrains.intellij.platform:util-ex:$intellijVersion")
        classpath("com.jetbrains.intellij.platform:indexing:$intellijVersion")
        classpath("com.jetbrains.intellij.amper:amper-deps-proprietary-xcode-model-squashed:$xcodeModelSquashedVersion")
        classpath("org.jetbrains.intellij.deps.fastutil:intellij-deps-fastutil:8.5.11-18")
        classpath(files("${core.pathString.replace('\\', '/')}"))
        classpath(files("${coreIntellij.pathString.replace('\\', '/')}"))
        classpath(files("${gradleIntegration.pathString.replace('\\', '/')}"))
        classpath(files("${frontendApi.pathString.replace('\\', '/')}"))
        classpath(files("${tomlPsi.pathString.replace('\\', '/')}"))
        classpath(files("${yamlPsi.pathString.replace('\\', '/')}"))
        classpath(files("${amperPsi.pathString.replace('\\', '/')}"))
        classpath(files("${parserStub.pathString.replace('\\', '/')}"))
        classpath(files("${schemaFrontend.pathString.replace('\\', '/')}"))
    }
}
plugins.apply("org.jetbrains.amper.settings.plugin")
        """.trimIndent()

        copyDirectory(Path("../.."), projectPath)
        projectPath.resolve("settings.gradle.kts").writeText(
            settingsContent,
            options = arrayOf(StandardOpenOption.TRUNCATE_EXISTING)
        )

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
                "publishToMavenLocal", "--stacktrace", "--no-build-cache", "-PinBootstrapMode=true")
            .setStandardOutput(TeeOutputStream(System.out, stdout))
            .setStandardError(TeeOutputStream(System.err, stderr))
            .run()
        val output = (stdout.toByteArray().decodeToString() + "\n" + stderr.toByteArray().decodeToString()).replace("\r", "")

        // then
        assertTrue { output.contains("BUILD SUCCESSFUL") }
    }

}

// TODO Replace by copyRecursively.
fun copyDirectory(source: Path, target: Path) {
    Files.walk(source).use { paths ->
        paths.forEach { sourcePath ->
            if (sourcePath.any { it.pathString == "build" || it.pathString == ".gradle" }) return@forEach

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
