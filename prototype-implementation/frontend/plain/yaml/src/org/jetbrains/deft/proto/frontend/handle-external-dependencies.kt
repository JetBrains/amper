package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.core.Result
import org.jetbrains.deft.proto.core.deftFailure
import org.jetbrains.deft.proto.core.messages.ProblemReporterContext
import org.jetbrains.deft.proto.frontend.dependency.parseDependency
import org.jetbrains.deft.proto.frontend.nodes.YamlNode
import org.jetbrains.deft.proto.frontend.nodes.pretty
import org.jetbrains.deft.proto.frontend.nodes.reportNodeError
import kotlin.io.path.Path

context (BuildFileAware)
class DefaultPotatoModuleDependency(
    private val depPath: String,
    override val compile: Boolean = true,
    override val runtime: Boolean = true,
    override val exported: Boolean = false,
    private val node: YamlNode,
) : PotatoModuleDependency {
    context (ProblemReporterContext)
    override val Model.module: Result<PotatoModule>
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
        }?.let { Result.success(it) } ?: run {
            val message = FrontendYamlBundle.message("cant.find.module", depPath)
            problemReporter.reportNodeError(
                message,
                node = node,
                file = buildFile,
            )
            deftFailure()
        }

    override fun toString(): String {
        return "InternalDependency(module=$depPath)"
    }
}

context (BuildFileAware, ProblemReporterContext, ParsingContext)
internal fun List<FragmentBuilder>.handleExternalDependencies(
    config: YamlNode.Mapping,
    osDetector: OsDetector = DefaultOsDetector()
): Result<Unit> = addRawDependencies(config, osDetector).also { addKotlinTestIfNotIncluded() }

context (BuildFileAware, ProblemReporterContext, ParsingContext)
private fun List<FragmentBuilder>.addRawDependencies(config: YamlNode.Mapping, osDetector: OsDetector): Result<Unit> =
    config.handleFragmentSettings<YamlNode.Sequence>(this, "dependencies") { depList ->
        var hasErrors = false
        val resolved = depList.mapNotNull { dep ->
            val dependency = parseDependency(dep)
            if (dependency == null) {
                problemReporter.reportNodeError(
                    FrontendYamlBundle.message("cant.parse.dependency", name, dep.pretty),
                    node = dep,
                    file = buildFile,
                )
                hasErrors = true
            }
            dependency
        }.map {
            if (it is MavenDependency) {
                replaceWithOsDependant(osDetector, it)
            } else it
        }
        if (hasErrors) {
            return@handleFragmentSettings deftFailure()
        }
        externalDependencies.addAll(resolved)
        Result.success(Unit)
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
