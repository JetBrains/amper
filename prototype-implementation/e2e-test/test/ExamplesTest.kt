import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.walk
import kotlin.reflect.full.declaredFunctions
import kotlin.test.Ignore
import kotlin.test.assertEquals

class ExamplesTest : E2ETestFixture("../../examples/") {
    @Test
    fun `check all example projects are tested`() {
        @OptIn(ExperimentalPathApi::class)
        val testProjects = Path.of(pathToProjects).walk().filter {
            it.name.startsWith("settings.gradle", ignoreCase = true)
        }.sorted()

        val testMethods = this::class.declaredFunctions.count {
            it.annotations.any { it.annotationClass.qualifiedName == "org.junit.jupiter.api.Test" }
        }

        assertEquals(testProjects.count(), testMethods - 1/*except this test method*/,
            message = "Unexpected tests count, there are ${testProjects.count()} test projects:\n"
                    + testProjects.joinToString("\n") { it.parent.pathString })
    }

    @Test
    fun `new project template runs and prints Hello, World`() = test(
        projectName = "new-project-template",
        "run",
        expectOutputToHave = "Hello, World!",
    )

    @Test
    fun `jvm-hello-world runs and prints Hello, World`() = test(
        projectName = "jvm-hello-world",
        "run",
        expectOutputToHave = "Hello, World!",
    )

    @Test
    fun `jvm-kotlin+java runs and prints Hello, World`() = test(
        projectName = "jvm-kotlin+java",
        "run",
        expectOutputToHave = "Hello, World",
    )

    @Test
    fun `jvm-with-tests test task fails`() = test(
        projectName = "jvm-with-tests",
        "test",
        expectOutputToHave = "> There were failing tests. See the report at: file:",
        shouldSucceed = false,
    )

    @Test
    fun `modularized test task succeeds`() = test(
        projectName = "modularized",
        "test",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    fun `multiplatform test task succeeds`() = test(
        projectName = "multiplatform",
        "test",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    fun `compose desktop build task`() = test(
        projectName = "compose-desktop",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    fun `compose desktop ios task`() = test(
        projectName = "compose-ios",
        "buildIosAppMain",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    fun `compose android build task`() = test(
        projectName = "compose-android",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    fun `templates build task`() = test(
        projectName = "templates",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    fun `variants builds`() = test(
        projectName = "build-variants",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    fun `gradle interop`() = test(
        projectName = "gradle-interop",
        "build", ":hello", ":run",
        expectOutputToHave = listOf(
            """
            > Task :hello
            Hello from Gradle task!
            """.trimIndent(),

            """
            > Task :run
            Hello, World!
            """.trimIndent(),

            "BUILD SUCCESSFUL"
        )
    )

    @Test
    fun `gradle migration jvm`() = test(
        projectName = "gradle-migration-jvm",
        "build", ":app:run",
        expectOutputToHave = listOf("Hello, World!", "BUILD SUCCESSFUL"),
    )

    @Test
    fun `gradle migration kmp`() = test(
        projectName = "gradle-migration-kmp",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )
}
