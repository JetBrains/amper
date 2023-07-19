import org.gradle.testkit.runner.GradleRunner
import java.nio.file.Path
import kotlin.test.assertContains


open class E2ETestFixture(private val pathToProjects: String) {
    internal fun test(
        projectName: String,
        vararg buildArguments: String,
        expectOutputToHave: String,
        shouldSucceed: Boolean = true,
    ) {
        // given
        val file = Path.of("${pathToProjects}/$projectName")

        // when
        val runner = GradleRunner.create()
            .withProjectDir(file.toFile())
            .withArguments(*buildArguments, "--stacktrace")
        val buildResult = if (shouldSucceed) runner.build() else runner.buildAndFail()

        // then
        assertContains(buildResult.output, expectOutputToHave)
    }
}
