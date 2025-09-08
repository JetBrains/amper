/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.maven

import com.github.ajalt.mordant.terminal.Terminal
import org.apache.maven.execution.DefaultMavenExecutionResult
import org.apache.maven.execution.MavenSession
import org.apache.maven.lifecycle.internal.LifecycleExecutionPlanCalculator
import org.apache.maven.model.Plugin
import org.apache.maven.plugin.MavenPluginManager
import org.apache.maven.plugin.Mojo
import org.apache.maven.plugin.MojoExecution
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.PlexusContainer
import org.codehaus.plexus.util.xml.Xpp3DomBuilder
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.maven.publish.createMavenExecutionRequest
import org.jetbrains.amper.maven.publish.createRepositorySession
import org.jetbrains.amper.maven.publish.mavenRepositorySystem
import org.jetbrains.amper.tasks.EmptyTaskResult
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.telemetry.use
import kotlin.io.path.Path
import kotlin.io.path.pathString

typealias MavenPlugin = Plugin
typealias MojoDesc = org.jetbrains.amper.frontend.plugins.Mojo

internal val PlexusContainer.mavenPluginManager: MavenPluginManager get() = lookup(MavenPluginManager::class.java)
internal val PlexusContainer.lifecycleExecutionPlanCalculator get() = lookup(LifecycleExecutionPlanCalculator::class.java)

class ExecuteMavenMojoTask(
    override val taskName: TaskName,
    val module: AmperModule,
    val buildOutputRoot: TaskOutputRoot,
    val terminal: Terminal,
    val plexus: PlexusContainer,
    val mavenPlugin: MavenPlugin,
    val mojo: MojoDesc,
    val mavenProject: MavenProject,
    val configString: String,
) : Task {
    val pluginCoordinates = "${mavenPlugin.groupId}:${mavenPlugin.artifactId}:${mavenPlugin.version}"
    val goalCoordinates = "$pluginCoordinates:${mojo.goal}"

    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext) =
        with(plexus) { run(dependenciesResult, executionContext) }

    suspend fun PlexusContainer.run(
        dependenciesResult: List<TaskResult>,
        executionContext: TaskGraphExecutionContext,
    ): TaskResult {
        val projectEmbryo = dependenciesResult.filterIsInstance<MavenProjectEmbryo>().single()
        val repoSession = createRepositorySession(Path(""))
        val request = mavenRepositorySystem.createMavenExecutionRequest(Path(""))

        val newMavenProject = MavenProject(mavenProject).apply {
            build.directory = buildOutputRoot.path.pathString
            artifact = DefaultArtifact("dummy", "dummy", "dummy", "runtime", "jar")
            projectEmbryo.configureProject(this)
        }
        
        val configDom = Xpp3DomBuilder.build(configString.reader())

        val mojoDesc = mavenPluginManager.getMojoDescriptor(
            /* plugin = */ mavenPlugin,
            /* goal = */ mojo.goal,
            /* repositories = */ emptyList(),
            /* session = */ repoSession,
        )

        val session = MavenSession(
            /* container = */ plexus,
            /* repositorySession = */ repoSession,
            /* request = */ request,
            /* result = */ DefaultMavenExecutionResult()
        ).apply { currentProject = newMavenProject }

        val mojoExecution = MojoExecution(
            /* mojoDescriptor = */ mojoDesc,
            /* executionId = */ "goal $goalCoordinates, execution: ${executionContext.executionId}",
            /* source = */ MojoExecution.Source.CLI
        ).apply { configuration = configDom }

        lifecycleExecutionPlanCalculator.setupMojoExecution(session, mavenProject, mojoExecution)
        val configuredMojo = mavenPluginManager.getConfiguredMojo(Mojo::class.java, session, mojoExecution)

        // Execute mojo.
        spanBuilder("Executing maven plugin mojo: $goalCoordinates")
            .use { configuredMojo.execute() }

        return EmptyTaskResult
    }
}