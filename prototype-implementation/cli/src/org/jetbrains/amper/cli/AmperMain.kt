package org.jetbrains.amper.cli

import org.slf4j.LoggerFactory
import org.tinylog.jul.JulTinylogBridge
import picocli.CommandLine.Command
import picocli.CommandLine.Help
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable

@Command(
    name = "amper",
    mixinStandardHelpOptions = true,
    version = ["amper early years"],
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

    // Provided by amper script
    @Suppress("unused")
    @Option(
        names = ["--from-sources"],
        description = ["Internal flag used by Amper script"],
        hidden = true,
    )
    var fromSources: Boolean = false

    override fun call(): Int {
        // TODO think of a better place to activate it. e.g. we need it in tests too
        JulTinylogBridge.activate()

        val root = projectRoot ?: Paths.get(System.getProperty("user.dir"))
        logger.info("Project Root: $root")

        val projectContext = ProjectContext.create(root)

        return AmperBackend.run(context = projectContext, tasksToRun = tasksToRun)
    }

    private val logger = LoggerFactory.getLogger(javaClass)
}
