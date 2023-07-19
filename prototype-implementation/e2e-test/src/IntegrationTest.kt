import org.junit.jupiter.api.Test
import kotlin.test.Ignore

class IntegrationTest : E2ETestFixture("./testData/projects/"){
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
}
