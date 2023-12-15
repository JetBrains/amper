@file:OptIn(ExperimentalPathApi::class)

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.pathString
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalPathApi::class)
class CliIntegrationTest {
    @Test
    @Ignore("not ready yet")
    @UnixOnly
    fun compileHelloWorldProject(@TempDir tempDir: Path) {
        val cli = TestUtil.prototypeImplementationRoot.resolve("cli/scripts/amper")
        assertTrue { cli.isExecutable() }

        val projectPath = TestUtil.prototypeImplementationRoot.resolve("amper-backend-test/testData/projects/language-version")
        assertTrue { projectPath.isDirectory() }

        projectPath.copyToRecursively(tempDir, followLinks = false, overwrite = false)

        val process = ProcessBuilder()
            .directory(tempDir.toFile())
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectErrorStream(true)
            .command(cli.pathString, "--from-sources")
            .start()

        process.outputStream.close()
        val processOutput = process.inputStream.readAllBytes().decodeToString()
        val rc = process.waitFor()

        assertEquals(0, rc, "Exit code must be 0. Process output:\n$processOutput")

        val expectedFile = tempDir.resolve("build/distributions/cli-jvm-hello-world-SNAPSHOT-1.0.zip")
        assertTrue("Expected output file to exist: $expectedFile. Process output:\n$processOutput") {
            expectedFile.exists()
        }
    }
}
