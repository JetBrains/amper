import org.gradle.internal.impldep.org.yaml.snakeyaml.Yaml
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertTrue

class BootstrapTest {

    @TempDir
    lateinit var projectPath: Path

    @Test
    fun `amper could build itself using version from sources`() {
        // given
        val commonTemplateString = Path.of("../common.module-template.yaml").toAbsolutePath().readText()
        val yamlMap = Yaml().load<Map<String, Map<String, Map<String, String>>>>(commonTemplateString)
        val version = yamlMap["settings"]?.get("publishing")?.get("version")

        val core = Path.of("../")
            .toAbsolutePath()
            .resolve("core/build/libs/core-jvm-$version.jar")
            .normalize()

        val gradleIntegration = Path.of("../")
            .toAbsolutePath()
            .resolve("gradle-integration/build/libs/gradle-integration-jvm-$version.jar")
            .normalize()

        val frontendApi = Path.of("../")
            .toAbsolutePath()
            .resolve("frontend-api/build/libs/frontend-api-jvm-$version.jar")
            .normalize()

        val plainFrontend = Path.of("../")
            .toAbsolutePath()
            .resolve("frontend/plain/yaml/build/libs/yaml-jvm-$version.jar")
            .normalize()

        val util = Path.of("../")
            .toAbsolutePath()
            .resolve("frontend/util/build/libs/util-jvm-$version.jar")
            .normalize()

        val settingsContent = """
buildscript {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }

    dependencies {
        classpath("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:1.9.0")
        classpath("org.yaml:snakeyaml:2.0")
        classpath(files("$core"))
        classpath(files("$gradleIntegration"))
        classpath(files("$frontendApi"))
        classpath(files("$plainFrontend"))
        classpath(files("$util"))
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
