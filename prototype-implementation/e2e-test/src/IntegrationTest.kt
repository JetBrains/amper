import org.junit.jupiter.api.Test

class IntegrationTest : E2ETestFixture("./testData/projects/") {
    @Test
    fun `running jvm basic`() = test(
        projectName = "jvm-basic",
        "run",
        expectOutputToHave = "Hello, World!",
    )

    @Test
    fun `configuring jvm dependencies`() = test(
        projectName = "jvm-dependencies",
        "test",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    fun `disabling junit platform`() = test(
        projectName = "disable-junit",
        ":cleanJvmTest", ":jvmTest", "--tests", "SimpleTest.test",
        shouldSucceed = false,
        expectOutputToHave = "> No tests found for given includes: [SimpleTest.test](--tests filter)",
    )

    @Test
    fun `if we have only one platform, platform sourceSet becomes common, so we need to apply settings to common and platform-specific sourceSet simultaneously`() =
        test(
            projectName = "language-version",
            "assemble",
            expectOutputToHave = "BUILD SUCCESSFUL",
        )

    @Test
    fun `jvm+android assembles`() = test(
        projectName = "jvm+android",
        "assemble",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    fun `compose desktop`() = test(
        projectName = "compose-desktop",
        "assemble",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    fun `compose android`() = test(
        projectName = "compose-android",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )
}
