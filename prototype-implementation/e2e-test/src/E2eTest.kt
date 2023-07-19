import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertContains

class E2eTest {
    @Test
    fun `new project template runs and prints Hello, World`() = test(
        exampleName = "new-project-template",
        "run",
        expectOutputToHave = "Hello, World!",
    )

    @Test
    fun `jvm-hello-world runs and prints Hello, World`() = test(
        exampleName = "jvm-hello-world",
        "run",
        expectOutputToHave = "Hello, World!",
    )

    @Test
    fun `jvm-android-hello-world assembles`() = test(
        exampleName = "jvm+android-hello-world",
        "assemble",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    fun `jvm-kotlin+java runs and prints Hello, World`() = test(
        exampleName = "jvm-kotlin+java",
        "run",
        expectOutputToHave = "Hello, World",
    )

    @Test
    fun `jvm-with-tests test task fails`() = test(
        exampleName = "jvm-with-tests",
        "test",
        expectOutputToHave = "java.lang.AssertionError at WorldTest.kt:13",
        shouldSucceed = false,
    )

    @Test
    fun `kmp-mobile test task succeeds`() = test(
        exampleName = "kmp-mobile",
        "test",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    fun `kmp-mobile-modularized test task succeeds`() = test(
        exampleName = "kmp-mobile-modularized",
        "test",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    @Test
    fun `variants builds`() = test(
        exampleName = "build-variants",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL",
    )

    private fun test(
        exampleName: String,
        vararg buildArguments: String,
        expectOutputToHave: String,
        shouldSucceed: Boolean = true,
    ) {
        // given
        val file = Path.of("../../examples/$exampleName")

        // when
        val runner = GradleRunner.create()
            .withProjectDir(file.toFile())
            .withArguments(*buildArguments, "--stacktrace")
        val buildResult = if (shouldSucceed) runner.build() else runner.buildAndFail()

        // then
        assertContains(buildResult.output, expectOutputToHave)
    }
}
