/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.LocalModuleDependency
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.isExplicitlySet
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.schema.JavaVersion
import org.jetbrains.amper.problems.reporting.BuildProblem
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.BuildProblemSource
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.FileBuildProblemSource
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.MultipleLocationsBuildProblemSource
import org.jetbrains.amper.problems.reporting.ProblemReporter
import kotlin.reflect.KProperty0

// TODO Use the configured JDK version when it becomes configurable. For now we hardcoded 21 in the backend
private val DefaultJdkVersion = JavaVersion.VERSION_21

object JvmReleaseLowerThanDependencies : AomModelDiagnosticFactory {

    override fun analyze(model: Model, problemReporter: ProblemReporter) {
        val reportedPlaces = mutableSetOf<Trace>()
        model.modules.forEach { module ->
            // A null JVM release means we use the JDK's default source/target/release configuration
            val thisJvmRelease = module.jvmRelease() ?: DefaultJdkVersion
            module.fragments
                // No need to warn for tests fragments, because any real problem would be noticed during the tests
                .filter { !it.isTest && it.platforms.any(Platform::isAffectedByJvmRelease) }
                .flatMap { it.externalDependencies }
                .filterIsInstance<LocalModuleDependency>()
                .forEach { dep ->
                    val depJvmRelease = dep.module.jvmRelease() ?: DefaultJdkVersion
                    if (depJvmRelease.releaseNumber > thisJvmRelease.releaseNumber && reportedPlaces.add(dep.trace)) {
                        problemReporter.reportMessage(
                            JvmReleaseTooLowForDependency(
                                module = module,
                                jvmRelease = thisJvmRelease,
                                dependency = dep,
                                dependencyJvmRelease = depJvmRelease,
                            )
                        )
                    }
                }
        }
    }
}

private fun Platform.isAffectedByJvmRelease() = this == Platform.JVM || this == Platform.ANDROID

class JvmReleaseTooLowForDependency(
    val module: AmperModule,
    val jvmRelease: JavaVersion,
    val dependency: LocalModuleDependency,
    val dependencyJvmRelease: JavaVersion,
) : BuildProblem {
    override val buildProblemId get() = diagnosticId

    private val jvmReleaseElement = module.jvmReleaseProperty()
    private val dependencyJvmReleaseProperty = dependency.module.jvmReleaseProperty()

    override val source: BuildProblemSource = MultipleLocationsBuildProblemSource(
        sources = listOfNotNull(
            dependency.asBuildProblemSource(),
            jvmReleaseElement?.asBuildProblemSource(),
            dependencyJvmReleaseProperty?.asBuildProblemSource(),
        ).filterIsInstance<FileBuildProblemSource>(),
        groupingMessage = SchemaBundle.message("module.dependency.with.incompatible.jvm.release.grouping")
    )
    override val message = SchemaBundle.message(
        "module.dependency.with.incompatible.jvm.release",
        module.userReadableName,
        jvmRelease.withDefaultMentionIf(jvmReleaseElement?.isExplicitlySet != true),
        dependency.module.userReadableName,
        dependencyJvmRelease.withDefaultMentionIf(dependencyJvmReleaseProperty?.isExplicitlySet != true),
    )

    private fun JavaVersion.withDefaultMentionIf(condition: Boolean): String {
        val numberString = releaseNumber.toString()
        return if (condition) "$numberString (default)" else numberString
    }

    override val level: Level get() = Level.Warning
    override val type: BuildProblemType get() = BuildProblemType.InconsistentConfiguration

    companion object {
        const val diagnosticId: BuildProblemId = "jvm.release.too.low.for.dependency"
    }
}

// We don't have to go through all fragments, this is marked @PlatformAgnostic so it must be consistent
private fun AmperModule.jvmRelease(): JavaVersion? = fragments.firstOrNull()?.settings?.jvm?.release

// We don't have to go through all fragments, this is marked @PlatformAgnostic so it must be consistent
private fun AmperModule.jvmReleaseProperty(): KProperty0<JavaVersion?>? =
    fragments.firstOrNull()?.settings?.jvm?.let { it::release }
