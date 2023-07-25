package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.util.requireSingleOrNull
import kotlin.io.path.Path


context (BuildFileAware)
class DefaultPotatoModuleDependency(
    private val depPath: String,
    override val compile: Boolean = true,
    override val runtime: Boolean = true,
    override val exported: Boolean = false,
) : PotatoModuleDependency {
    override val Model.module: PotatoModule
        get() = modules.find {
            val source = it.source as? PotatoModuleFileSource
                ?: return@find false

            val targetModulePotFilePath = source.buildFile.toAbsolutePath()

            val dependencyPath = Path(depPath)
            val sourceModulePotFilePath = if (dependencyPath.isAbsolute) {
                dependencyPath
                    .resolve("Pot.yaml")
            } else {
                buildFile.parent
                    .resolve(dependencyPath)
                    .resolve("Pot.yaml")
                    .normalize()
                    .toAbsolutePath()
            }

            val sourceModuleGradleFilePath =
                buildFile.parent.resolve("$depPath/build.gradle.kts").normalize().toAbsolutePath()

            targetModulePotFilePath == sourceModulePotFilePath || targetModulePotFilePath == sourceModuleGradleFilePath
        } ?: parseError("No module $depPath found")

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
internal fun List<FragmentBuilder>.handleExternalDependencies(
    config: Settings,
    osDetector: OsDetector = DefaultOsDetector()
) = addRawDependencies(config, osDetector).also { addKotlinTestIfNotIncluded() }

context (Map<String, Set<Platform>>, BuildFileAware)
private fun List<FragmentBuilder>.addRawDependencies(config: Settings, osDetector: OsDetector) {
    config.handleFragmentSettings<List<Any>>(this, "dependencies") { depList ->
        val resolved = depList.map { dep ->
            when (dep) {
                is String -> {
                    val flags = dep.searchFlags()
                    val notation = "^\\S+".toRegex().findAll(dep).single().value
                    if (dep.startsWith(".") || dep.startsWith("/")) {
                        DefaultPotatoModuleDependency(notation, flags.compile, flags.runtime, flags.exported)
                    } else {
                        replaceWithOsDependant(
                            osDetector,
                            MavenDependency(notation, flags.compile, flags.runtime, flags.exported)
                        )
                    }
                }

                is Map<*, *> -> {
                    dep as Settings
                    val path = dep.getStringValue("path")
                    val notation = dep.getStringValue("notation")
                    val compile = dep.getValue<Boolean>("compile") ?: true
                    val runtime = dep.getValue<Boolean>("runtime") ?: true
                    val exported = dep.getValue<Boolean>("exported") ?: true
                    if (path != null) {
                        DefaultPotatoModuleDependency(path, compile, runtime, exported)
                    } else if (notation != null) {
                        replaceWithOsDependant(osDetector, MavenDependency(notation, compile, runtime, exported))
                    } else parseError("Error while parsing dependencies for fragment $name")
                }

                else -> parseError("Error while parsing dependencies for fragment $name")
            }
        }
        externalDependencies.addAll(resolved)
    }
}

private fun List<FragmentBuilder>.addKotlinTestIfNotIncluded() {
    filter { it.variants.contains("test") }
        .singleOrNull { it.dependencies.none { it.dependencyKind == MutableFragmentDependency.DependencyKind.Refines } }
        ?.let { fragment ->
            val isKotlinTestNotIncluded = fragment.externalDependencies
                .filterIsInstance<MavenDependency>()
                .none { it.coordinates.startsWith("org.jetbrains.kotlin:kotlin-test") }
            if (isKotlinTestNotIncluded) {
                fragment.externalDependencies.add(MavenDependency("org.jetbrains.kotlin:kotlin-test:1.8.20"))
            }
        }
}

fun replaceWithOsDependant(osDetector: OsDetector, mavenDependency: MavenDependency): MavenDependency {
    if (mavenDependency.coordinates.startsWith("org.jetbrains.compose.desktop:desktop-jvm:")) {
        return MavenDependency(
            mavenDependency.coordinates.replace(
                "org.jetbrains.compose.desktop:desktop-jvm",
                "org.jetbrains.compose.desktop:desktop-jvm-${osDetector.detect().value}"
            ),
            mavenDependency.compile,
            mavenDependency.runtime,
            mavenDependency.exported,
        )
    }
    if (mavenDependency.coordinates.startsWith("org.jetbrains.compose.desktop:desktop:")) {
        return MavenDependency(
            mavenDependency.coordinates.replace(
                "org.jetbrains.compose.desktop:desktop",
                "org.jetbrains.compose.desktop:desktop-jvm-${osDetector.detect().value}"
            ),
            mavenDependency.compile,
            mavenDependency.runtime,
            mavenDependency.exported,
        )
    }
    return mavenDependency
}
