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
    fun `jvm-android-hello-world assembles`() = test(
        projectName = "jvm+android-hello-world",
        "assemble",
        expectOutputToHave = "BUILD SUCCESSFUL",
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
    fun `kmp-mobile test task succeeds`() = test(
        projectName = "kmp-mobile",
        "test",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    fun `kmp-mobile-modularized test task succeeds`() = test(
        projectName = "kmp-mobile-modularized",
        "test",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    fun `variants builds`() = test(
        projectName = "build-variants",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )
}
