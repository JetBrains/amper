package org.jetbrains.deft.proto.frontend

import java.nio.file.Path
import java.util.ServiceLoader

interface ModelInit {

    companion object {
        fun getModel(root: Path): Model {
            val services = ServiceLoader.load(ModelInit::class.java)
            val foundService = services.iterator().run {
                check(hasNext()) { "No ModelInit service found" }
                val result = next()
                check(!hasNext()) { "Only one Model init service should be present" }
                result
            }
            return foundService.getModel(root)
        }
    }

    fun getModel(root: Path): Model

}