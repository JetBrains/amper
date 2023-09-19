package org.jetbrains.deft.proto.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.jetbrains.deft.proto.core.Result
import org.jetbrains.deft.proto.core.get
import org.jetbrains.deft.proto.core.messages.*
import org.jetbrains.deft.proto.frontend.Model
import org.jetbrains.deft.proto.frontend.ModelInit
import org.jetbrains.deft.proto.frontend.resolve.resolved
import org.slf4j.LoggerFactory

@Suppress("unused") // Is passed via implementationClass option when declaring a plugin in the Gradle script.
class BindingSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val rootPath = settings.rootDir.toPath().toAbsolutePath()
        with(SLF4JProblemReporterContext()) {
            val model = ModelInit.getModel(rootPath)
            if (model is Result.Failure<Model>) {
                throw GradleException(problemReporter.getGradleError())
            }

            // Use [ModelWrapper] to cache and preserve links on [PotatoModule].
            val modelWrapper = ModelWrapper(model.get().resolved)
            SettingsPluginRun(settings, modelWrapper).run()
        }
    }
}

internal class SLF4JProblemReporterContext : ProblemReporterContext {
    override val problemReporter: SLF4JProblemReporter = SLF4JProblemReporter(BindingSettingsPlugin::class.java)
}

internal class SLF4JProblemReporter(loggerClass: Class<*> = ProblemReporter::class.java) : CollectingProblemReporter() {
    companion object {
        private const val ERROR_PREFIX = "  - "
        private const val ERROR_INDENT = "    "
    }

    private val logger = LoggerFactory.getLogger(loggerClass)

    override fun doReportMessage(message: BuildProblem) {
        when (message.level) {
            Level.Warning -> logger.warn(renderMessage(message))
            Level.Error -> logger.error(renderMessage(message))
        }
    }

    fun getErrors(): List<String> = problems.map(::renderMessage)

    fun getGradleError(): String =
        """
        |Deft model initialization failed. 
        |Errors: 
        |$ERROR_PREFIX${getErrors().joinToString("\n|$ERROR_PREFIX") { it.replace("\n", "\n$ERROR_INDENT") }}
        |See logs for details.""".trimMargin()
}
