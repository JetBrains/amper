package org.jetbrains.deft.proto.frontend

import java.nio.file.Path
import java.util.ServiceLoader

interface ModelInit {

    companion object {

        const val MODEL_NAME_ENV = "DEFT_MODEL_TYPE"

        const val MODEL_NAME_PROPERTY = "org.jetbrains.deft.model.type"

        fun getModel(root: Path): Model {
            val services = ServiceLoader.load(ModelInit::class.java).associateBy { it.name }
            check(services.isNotEmpty()) { "No ModelInit service found" }
            val modelName = System.getProperty(MODEL_NAME_PROPERTY)
                    ?: System.getenv(MODEL_NAME_ENV)
                    ?: "plain"
            return services[modelName]?.getModel(root)
                    ?: error("Model with name $modelName is not found!")
        }
    }

    /**
     * A way to distinguish different models.
     */
    val name: String

    fun getModel(root: Path): Model

}