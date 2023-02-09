package org.example.api

import java.nio.file.Path
import java.util.ServiceLoader

interface ModelInit {

    companion object {
        fun getModel(root: Path): Model {
            val services = ServiceLoader.load(ModelInit::class.java)
            val foundService = services.iterator().run {
                assert(hasNext()) { "No ModelInit service found" }
                val result = next()
                assert(!hasNext()) { "Only one Model init service should be present" }
                result
            }
            return foundService.getModel(root)
        }
    }

    fun getModel(root: Path): Model

}

typealias CollapsedMap = Map<String, List<String>>

interface Model {

    companion object {

        /**
         * When no target is specified.
         */
        const val defaultTarget = "default-target"
    }

    /**
     * Get modules (module ids) list for project.
     */
    val modules: List<Pair<String, Path>>

    /**
     * Get available targets for module. Must return at least one target.
     */
    fun getTargets(moduleId: String): List<String>

    /**
     * Get dependencies (moduleId list) for specified module and specified target.
     */
    fun getDeclaredDependencies(moduleId: String, targetId: String): List<String>

    fun getModuleInfo(moduleId: String): CollapsedMap

    fun getKotlinInfo(moduleId: String): CollapsedMap

}