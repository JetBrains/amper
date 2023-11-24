import org.gradle.internal.impldep.org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Files
import java.util.concurrent.ArrayBlockingQueue
import kotlin.jvm.optionals.getOrNull

object GradleDaemonManager : BeforeEachCallback, AfterTestExecutionCallback {

    private const val usedDaemonKey = "Used gradle daemon"

    private const val numberOfDaemons = 4

    init {
        println("E2E tests using $numberOfDaemons gradle test daemons")
    }

    private val availableGradleDaemons = ArrayBlockingQueue<GradleRunner>(numberOfDaemons).apply {
        repeat(numberOfDaemons) {
            add(GradleRunner.create().withTestKitDir(createTempDir()))
        }
    }

    override fun beforeEach(context: ExtensionContext) {
        val testInstance = context.testInstance.getOrNull() as? E2ETestFixture ?: return
        val takenDaemon = availableGradleDaemons.take()
        context.store.put(usedDaemonKey, takenDaemon)
        testInstance.gradleRunner = takenDaemon
    }

    override fun afterTestExecution(context: ExtensionContext) {
        val usedDaemon = context.store.remove(usedDaemonKey, GradleRunner::class.java)
        if (!availableGradleDaemons.offer(usedDaemon)) error("Unreachable")
    }

    private fun createTempDir() = Files.createTempDirectory("junit")
        .toFile()
        .also { FileUtils.forceDeleteOnExit(it) }

    private val ExtensionContext.store get() = getStore(namespace)
    private val ExtensionContext.namespace get() = ExtensionContext.Namespace.create(javaClass, requiredTestMethod)
}