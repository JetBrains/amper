package org.jetbrains.amper.cli

import org.jetbrains.amper.core.AmperBuild
import org.slf4j.LoggerFactory
import org.tinylog.jul.JulTinylogBridge
import picocli.CommandLine.Command
import picocli.CommandLine.Help
import picocli.CommandLine.IVersionProvider
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable

@Command(
    name = "amper",
    mixinStandardHelpOptions = true,
    versionProvider = AmperMain.VersionProvider::class,
    description = ["Amper entry point"]
)
class AmperMain : Callable<Int> {
    @Parameters(description = ["Tasks to run"])
    val tasksToRun = mutableListOf<String>()

    @Option(
        names = ["--root"],
        description = ["Project root"],
        showDefaultValue = Help.Visibility.NEVER,
    )
    var projectRoot: Path? = null

    override fun call(): Int {
        // TODO think of a better place to activate it. e.g. we need it in tests too
        JulTinylogBridge.activate()

        val root = projectRoot ?: Paths.get(System.getProperty("user.dir"))
        logger.info("Project Root: $root")

        val projectContext = ProjectContext.create(root)

        return AmperBackend.run(context = projectContext, tasksToRun = tasksToRun)
    }

    internal class VersionProvider: IVersionProvider {
        override fun getVersion(): Array<String> = arrayOf("${AmperBuild.PRODUCT_NAME} ${AmperBuild.BuildNumber}")
    }

    private val logger = LoggerFactory.getLogger(javaClass)
}
