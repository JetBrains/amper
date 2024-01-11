import org.gradle.testkit.runner.GradleRunner
import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.CollectingProblemReporter
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.schemaConverter.psi.ConvertCtx
import org.jetbrains.amper.frontend.schemaConverter.psi.convertTemplate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.*
import kotlin.io.path.Path
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.test.assertTrue

class BootstrapTest {

    @TempDir
    lateinit var projectPath: Path

    @Test
    fun `amper could build itself using version from sources`() {
        // given
        val commonTemplatePath = TestUtil.prototypeImplementationRoot.resolve("common.module-template.yaml")
        val reportingContext = TestProblemReporterContext()
        val context = ConvertCtx(commonTemplatePath.parent, FrontendPathResolver())
        val template = with(reportingContext) {
            with(context) {
                convertTemplate(commonTemplatePath)!!
            }
        }
        val version = template.settings.get(emptySet())?.publishing?.version

        val core = TestUtil.prototypeImplementationRoot.resolve("core/build/libs/core-jvm-$version.jar")
        val gradleIntegration = TestUtil.prototypeImplementationRoot.resolve("gradle-integration/build/libs/gradle-integration-jvm-$version.jar")

        println("############ gradle-integration version: $version")
        println("############ gradle-integration libraries: ${TestUtil.prototypeImplementationRoot.resolve("gradle-integration/build/libs").toFile().list()?.toSet()}")

        val frontendApi = TestUtil.prototypeImplementationRoot.resolve("frontend-api/build/libs/frontend-api-jvm-$version.jar")
        val yamlPsi = TestUtil.prototypeImplementationRoot.resolve("frontend/plain/yaml-psi/build/libs/yaml-psi-jvm-$version.jar")
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
        classpath("com.jetbrains.intellij.platform:core-impl:232.10203.20")
        classpath("com.jetbrains.intellij.platform:core-ui:232.10203.20")
        classpath("com.jetbrains.intellij.platform:util:232.10203.20")
        classpath("com.jetbrains.intellij.platform:util-base:232.10203.20")
        classpath("com.jetbrains.intellij.platform:util-ui:232.10203.20")
        classpath("com.jetbrains.intellij.platform:util-ex:232.10203.20")
        classpath("com.jetbrains.intellij.platform:indexing:232.10203.20")
        classpath("org.jetbrains.intellij.deps.fastutil:intellij-deps-fastutil:8.5.11-18")
        classpath(files("${core.pathString.replace('\\', '/')}"))
        classpath(files("${gradleIntegration.pathString.replace('\\', '/')}"))
        classpath(files("${frontendApi.pathString.replace('\\', '/')}"))
        classpath(files("${yamlPsi.pathString.replace('\\', '/')}"))
        classpath(files("${schemaFrontend.pathString.replace('\\', '/')}"))
        classpath(files("${util.pathString.replace('\\', '/')}"))
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
        val buildResult = GradleRunner.create()
            .withProjectDir(projectPath.toFile())
            .withArguments(
//                "-Dorg.gradle.jvmargs=-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005",
                "publishToMavenLocal", "--stacktrace", "-PinBootstrapMode=true")
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

class TestProblemReporter : CollectingProblemReporter() {
    override fun doReportMessage(message: BuildProblem) {}

    fun getErrors(): List<BuildProblem> = problems.filter { it.level == Level.Error || it.level == Level.Fatal }
}

class TestProblemReporterContext : ProblemReporterContext {
    override val problemReporter: TestProblemReporter = TestProblemReporter()
}
