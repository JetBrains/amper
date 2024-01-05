import org.gradle.internal.impldep.org.yaml.snakeyaml.Yaml
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertTrue

class BootstrapTest {

    @TempDir
    lateinit var projectPath: Path

    @Test
    fun `amper could build itself using version from sources`() {
        // given
        val commonTemplateString = TestUtil.prototypeImplementationRoot.resolve("common.module-template.yaml").readText()
        val yamlMap = Yaml().load<Map<String, Map<String, Map<String, String>>>>(commonTemplateString)
        val version = yamlMap["settings"]?.get("publishing")?.get("version")

        val core = TestUtil.prototypeImplementationRoot.resolve("core/build/libs/core-jvm-$version.jar")
        val gradleIntegration = TestUtil.prototypeImplementationRoot.resolve("gradle-integration/build/libs/gradle-integration-jvm-$version.jar")
        val frontendApi = TestUtil.prototypeImplementationRoot.resolve("frontend-api/build/libs/frontend-api-jvm-$version.jar")
        val plainFrontend = TestUtil.prototypeImplementationRoot.resolve("frontend/plain/yaml/build/libs/yaml-jvm-$version.jar")
        val schemaFrontend = TestUtil.prototypeImplementationRoot.resolve("frontend/schema/build/libs/schema-jvm-$version.jar")
        val util = TestUtil.prototypeImplementationRoot.resolve("frontend/util/build/libs/util-jvm-$version.jar")

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
        classpath("org.jetbrains.kotlin:kotlin-serialization:1.9.20")
        classpath("com.jetbrains.intellij.platform:core:232.10203.20")
        classpath("org.yaml:snakeyaml:2.0")
        classpath(files("${core.pathString.replace('\\', '/')}"))
        classpath(files("${gradleIntegration.pathString.replace('\\', '/')}"))
        classpath(files("${frontendApi.pathString.replace('\\', '/')}"))
        classpath(files("${plainFrontend.pathString.replace('\\', '/')}"))
        classpath(files("${schemaFrontend.pathString.replace('\\', '/')}"))
        classpath(files("${util.pathString.replace('\\', '/')}"))
    }
}
plugins.apply("org.jetbrains.amper.settings.plugin")
        """.trimIndent()

        copyDirectory(Path.of("../"), projectPath)
        projectPath.resolve("settings.gradle.kts").writeText(
            settingsContent,
            options = arrayOf(StandardOpenOption.TRUNCATE_EXISTING)
        )

        // when
        val buildResult = GradleRunner.create()
            .withProjectDir(projectPath.toFile())
            .withArguments("publishToMavenLocal", "--stacktrace", "-PinBootstrapMode=true")
            .build()

        // then
        assertTrue { buildResult.output.contains("BUILD SUCCESSFUL") }
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
