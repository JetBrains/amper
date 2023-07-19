import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertContains

class E2eTest {
    @Test
    fun `new project template runs and prints Hello, World`() {
        // get test resources folder
        val file = Path.of("../../examples/new-project-template")
        // run gradle
        val buildResult = GradleRunner.create()
            .withProjectDir(file.toFile())
            .withArguments("run", "--stacktrace")
            .build()

        assertContains(buildResult.output, "Hello, World!")
    }

    @Test
    fun `jvm-hello-world runs and prints Hello, World`() {
        // get test resources folder
        val file = Path.of("../../examples/jvm-hello-world")
        // run gradle
        val buildResult = GradleRunner.create()
            .withProjectDir(file.toFile())
            .withArguments("run", "--stacktrace")
            .build()

        assertContains(buildResult.output, "Hello, World!")
    }

    @Test
    fun `jvm-android-hello-world assembles`() {
        // get test resources folder
        val file = Path.of("../../examples/jvm+android-hello-world")
        // run gradle
        val buildResult = GradleRunner.create()
            .withProjectDir(file.toFile())
            .withArguments("assemble")
            .withDebug(true)
            .build()

        assertContains(buildResult.output, "BUILD SUCCESSFUL")
    }

    @Test
    fun `jvm-kotlin+java runs and prints Hello, World`() {
        // given
        val file = Path.of("../../examples/jvm-kotlin+java")

        // when
        val buildResult = GradleRunner.create()
            .withProjectDir(file.toFile())
            .withArguments("run", "--stacktrace")
            .build()

        // then
        assertContains(buildResult.output, "Hello, World")
    }

    @Test
    fun `jvm-with-tests test task fails`() {
        // given
        val file = Path.of("../../examples/jvm-with-tests")

        // when
        @Suppress("UnstableApiUsage") val buildResult = GradleRunner.create()
            .withProjectDir(file.toFile())
            .withArguments("test", "--stacktrace")
            .run()

        // then
        assertContains(buildResult.output, "java.lang.AssertionError at WorldTest.kt:13")
    }

    @Test
    fun `kmp-mobile test task fails`() {
        // given
        val file = Path.of("../../examples/kmp-mobile")

        // when
        @Suppress("UnstableApiUsage") val buildResult = GradleRunner.create()
            .withProjectDir(file.toFile())
            .withArguments("test", "--stacktrace")
            .withDebug(true)
            .run()

        // then
        assertContains(buildResult.output, "BUILD SUCCESSFUL")
    }

    @Test
    fun `kmp-mobile-modularized test task fails`() {
        // given
        val file = Path.of("../../examples/kmp-mobile-modularized")

        // when
        @Suppress("UnstableApiUsage") val buildResult = GradleRunner.create()
            .withProjectDir(file.toFile())
            .withArguments("test", "--stacktrace")
            .withDebug(true)
            .run()

        // then
        assertContains(buildResult.output, "BUILD SUCCESSFUL")
    }

    @Test
    fun `variants builds`() {
        // given
        val file = Path.of("../../examples/build-variants")

        // when
        val buildResult = GradleRunner.create()
            .withProjectDir(file.toFile())
            .withArguments("build", "--stacktrace")
            .withDebug(true)
            .build()
        // then
        assertContains(buildResult.output, "BUILD SUCCESSFUL")
    }
}