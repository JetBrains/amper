/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.core.*
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.dependency.parseDependency
import org.jetbrains.amper.frontend.nodes.YamlNode
import org.jetbrains.amper.frontend.nodes.pretty
import org.jetbrains.amper.frontend.nodes.reportNodeError
import java.nio.file.Path
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

            fun Path.resolveModuleFile() =
                this.resolve("module.yaml")

            val sourceModulePotFilePath = if (dependencyPath.isAbsolute) {
                dependencyPath.resolveModuleFile()
            } else {
                buildFile.parent
                    .resolve(dependencyPath)
                    .resolveModuleFile()
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
            amperFailure()
        }

    override fun toString(): String {
        return "InternalDependency(module=$depPath)"
    }
}

context (BuildFileAware, ProblemReporterContext, ParsingContext)
internal fun List<FragmentBuilder>.handleExternalDependencies(
    config: YamlNode.Mapping,
    systemInfo: SystemInfo = DefaultSystemInfo
): Result<Unit> = addRawDependencies(config, systemInfo)

context (BuildFileAware, ProblemReporterContext, ParsingContext)
private fun List<FragmentBuilder>.addRawDependencies(
    config: YamlNode.Mapping,
    systemInfo: SystemInfo = DefaultSystemInfo
): Result<Unit> =
    config.handleFragmentSettings<YamlNode.Sequence>(this, "dependencies") { depList ->
        var hasErrors = false
        val resolved = depList.mapNotNull { dep ->
            val dependency = BuiltInCatalog.parseDependency(dep)
            if (dependency == null) {
                problemReporter.reportNodeError(
                    FrontendYamlBundle.message("cant.parse.dependency", name, dep.pretty),
                    node = dep,
                    file = buildFile,
                )
                hasErrors = true
            }
            dependency
        }.mapNotNull {
            if (it.getOrNull() is MavenDependency) {
                replaceWithOsDependant(systemInfo, it.get() as MavenDependency)
            } else it.getOrElse {
                hasErrors = true
                null
            }
        }
        if (hasErrors) {
            return@handleFragmentSettings amperFailure()
        }
        externalDependencies.addAll(resolved)
        Result.success(Unit)
    }

fun replaceWithOsDependant(systemInfo: SystemInfo = DefaultSystemInfo, mavenDependency: MavenDependency): MavenDependency {
    if (mavenDependency.coordinates.startsWith("org.jetbrains.compose.desktop:desktop-jvm:")) {
        return MavenDependency(
            mavenDependency.coordinates.replace(
                "org.jetbrains.compose.desktop:desktop-jvm",
                "org.jetbrains.compose.desktop:desktop-jvm-${systemInfo.detect().familyArch}"
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
                "org.jetbrains.compose.desktop:desktop-jvm-${systemInfo.detect().familyArch}"
            ),
            mavenDependency.compile,
            mavenDependency.runtime,
            mavenDependency.exported,
        )
    }
    return mavenDependency
}
