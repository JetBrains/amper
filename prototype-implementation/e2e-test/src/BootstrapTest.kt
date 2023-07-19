import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.io.path.writeText
import kotlin.test.Ignore
import kotlin.test.assertTrue

class BootstrapTest {

    @TempDir
    lateinit var projectPath: Path

    @Test
    @Ignore
    fun `deft could build itself using version from sources`() {
        // given
        val gradleIntegration = Path.of("../")
            .toAbsolutePath()
            .resolve("gradle-integration/build/libs/gradle-integration-jvm-1.0-SNAPSHOT.jar")
            .normalize()

        val frontendApi = Path.of("../")
            .toAbsolutePath()
            .resolve("frontend-api/build/libs/frontend-api-jvm-1.0-SNAPSHOT.jar")
            .normalize()

        val plainFrontend = Path.of("../")
            .toAbsolutePath()
            .resolve("frontend/plain/yaml/build/libs/yaml-jvm-1.0-SNAPSHOT.jar")
            .normalize()

        val util = Path.of("../")
            .toAbsolutePath()
            .resolve("frontend/util/build/libs/util-jvm-1.0-SNAPSHOT.jar")
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
        classpath("com.github.johnrengelman:shadow:8.1.1")
        classpath(files("$gradleIntegration"))
        classpath(files("$frontendApi"))
        classpath(files("$plainFrontend"))
        classpath(files("$util"))
    }
}
plugins.apply("org.jetbrains.deft.proto.settings.plugin")
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