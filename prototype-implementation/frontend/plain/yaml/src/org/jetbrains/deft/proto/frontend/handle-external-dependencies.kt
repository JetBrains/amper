package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.util.requireSingleOrNull


context (BuildFileAware)
class DefaultPotatoModuleDependency(
    private val depPath: String,
    override val compile: Boolean = true,
    override val runtime: Boolean = true,
    override val exported: Boolean = false,
) : PotatoModuleDependency {
    override val Model.module: PotatoModule
        get() = modules.find {
            if (it.source is PotatoModuleFileSource) {
                val targetModulePotFilePath =
                    (it.source as PotatoModuleFileSource).buildFile.toAbsolutePath()
                val sourceModulePotFilePath =
                    buildFile.parent.resolve("$depPath/Pot.yaml").normalize().toAbsolutePath()

                val sourceModuleGradleFilePath =
                    buildFile.parent.resolve("$depPath/build.gradle.kts").normalize().toAbsolutePath()

                targetModulePotFilePath == sourceModulePotFilePath || targetModulePotFilePath == sourceModuleGradleFilePath
            } else {
                false
            }
        } ?: error("No module $depPath found")

    override fun toString(): String {
        return "InternalDependency(module=$depPath)"
    }
}

private fun String.searchFlags(): DefaultScopedNotation {
    fun MatchResult.toBooleanFlag() = !groupValues[1].startsWith("!")
    fun Regex.parseFlag() = findAll(this@searchFlags).requireSingleOrNull()?.toBooleanFlag()
    return object : DefaultScopedNotation {
        override val compile = "[,\\s]((!)?compile)".toRegex().parseFlag() ?: true
        override val runtime = "[,\\s]((!)?runtime)".toRegex().parseFlag() ?: true
        override val exported = "[,\\s]((!)?exported)".toRegex().parseFlag() ?: false
    }
}

context (Map<String, Set<Platform>>, BuildFileAware)
internal fun List<FragmentBuilder>.handleExternalDependencies(config: Settings) {
    config.handleFragmentSettings<List<Any>>(this, "dependencies") { depList ->
        val resolved = depList.map { dep ->
            when (dep) {
                is String -> {
                    val flags = dep.searchFlags()
                    val notation = "^\\S+".toRegex().findAll(dep).single().value
                    if (dep.startsWith(".")) {
                        DefaultPotatoModuleDependency(notation, flags.compile, flags.runtime, flags.exported)
                    } else {
                        MavenDependency(notation, flags.compile, flags.runtime, flags.exported)
                    }
                }
                is Map<*, *> -> {
                    dep as Settings
                    val path = dep.getValue<String>("path")
                    val notation = dep.getValue<String>("notation")
                    val compile = dep.getValue<Boolean>("compile") ?: true
                    val runtime = dep.getValue<Boolean>("runtime") ?: true
                    val exported = dep.getValue<Boolean>("exported") ?: true
                    if (path != null) {
                        DefaultPotatoModuleDependency(path, compile, runtime, exported)
                    } else if (notation != null) {
                        MavenDependency(notation, compile, runtime, exported)
                    } else error("Error while parsing dependencies for fragment $name")
                }

                else -> error("Error while parsing dependencies for fragment $name")
            }

        }
        externalDependencies.addAll(resolved)
    }
}