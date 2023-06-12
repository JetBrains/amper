import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertContains

class E2eTest {

    @Test
    fun `jvm-hello-world runs and prints Hello, World`() {
        // get test resources folder
        val file = Path.of("resources/jvm-hello-world")
        // run gradle
        val buildResult = GradleRunner.create()
            .withProjectDir(file.toFile())
            .withArguments("run")
            .build()

        assertContains(buildResult.output, "Hello, World!")
    }

    @Test
    fun `jvm-kotlin+java runs and prints Hello, World`() {
        // given
        val file = Path.of("resources/jvm-kotlin+java")

        // when
        val buildResult = GradleRunner.create()
            .withProjectDir(file.toFile())
            .withArguments("run")
            .build()

        // then
        assertContains(buildResult.output, "Hello, World")
    }

    @Test
    fun `jvm-with-tests test task fails`() {
        // given
        val file = Path.of("resources/jvm-with-tests")

        // when
        @Suppress("UnstableApiUsage") val buildResult = GradleRunner.create()
            .withProjectDir(file.toFile())
            .withArguments("test")
            .run()

        // then
        assertContains(buildResult.output, "org.opentest4j.AssertionFailedError at WorldTest.kt:7")
    }

    @Test
    fun `kmp-mobile test task fails`() {
        // given
        val file = Path.of("resources/kmp-mobile")

        // when
        @Suppress("UnstableApiUsage") val buildResult = GradleRunner.create()
            .withProjectDir(file.toFile())
            .withArguments("test")
            .run()

        // then
        assertContains(buildResult.output, "There were failing tests. See the report at")
    }

    @Test
    fun `kmp-mobile-modularized test task fails`() {
        // given
        val file = Path.of("resources/kmp-mobile-modularized")

        // when
        @Suppress("UnstableApiUsage") val buildResult = GradleRunner.create()
            .withProjectDir(file.toFile())
            .withArguments("test")
            .run()

        // then
        assertContains(buildResult.output, "There were failing tests. See the report at")
    }

    @Test
    fun `variants builds`() {
        // given
        val file = Path.of("resources/variants")

        // when
        @Suppress("UnstableApiUsage") val buildResult = GradleRunner.create()
            .withProjectDir(file.toFile())
            .withArguments("build")
            .build()
        // then
        assertContains(buildResult.output, "BUILD SUCCESSFUL")
    }
}