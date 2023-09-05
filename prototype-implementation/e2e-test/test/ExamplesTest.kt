import org.junit.jupiter.api.Test

class ExamplesTest : E2ETestFixture("../../examples/") {
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
    fun `gradle migration`() = test(
        projectName = "gradle-migration",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )
}
