package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.core.Result
import org.jetbrains.deft.proto.core.deftFailure
import org.jetbrains.deft.proto.core.messages.ProblemReporterContext
import java.nio.file.Path
import java.util.*

interface ModelInit {
    companion object {
        const val MODEL_NAME_ENV = "DEFT_MODEL_TYPE"

        const val MODEL_NAME_PROPERTY = "org.jetbrains.deft.model.type"

        context(ProblemReporterContext)
        fun getModel(root: Path): Result<Model> {
            val services = ServiceLoader.load(ModelInit::class.java).associateBy { it.name }
            if (services.isEmpty()) {
                problemReporter.reportError(FrontendApiBundle.message("no.model.init.service.found"))
                return deftFailure()
            }

            val modelName = System.getProperty(MODEL_NAME_PROPERTY)
                ?: System.getenv(MODEL_NAME_ENV)
                ?: "plain"
            val service = services[modelName]
            if (service == null) {
                problemReporter.reportError(FrontendApiBundle.message("model.not.found", modelName))
                return deftFailure()
            }

            return service.getModel(root)
        }
    }

    /**
     * A way to distinguish different models.
     */
    val name: String

    context(ProblemReporterContext)
    fun getModel(root: Path): Result<Model>
}
