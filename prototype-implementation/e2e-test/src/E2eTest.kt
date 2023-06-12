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
            .withArguments("run", "--stacktrace")
            .build()

        assertContains(buildResult.output, "Hello, World!")
    }
}