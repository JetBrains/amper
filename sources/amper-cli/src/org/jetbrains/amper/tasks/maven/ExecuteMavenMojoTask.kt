/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.maven

import com.github.ajalt.mordant.terminal.Terminal
import org.apache.maven.bridge.MavenRepositorySystem
import org.apache.maven.execution.DefaultMavenExecutionResult
import org.apache.maven.execution.MavenSession
import org.apache.maven.lifecycle.internal.LifecycleExecutionPlanCalculator
import org.apache.maven.model.Plugin
import org.apache.maven.plugin.BuildPluginManager
import org.apache.maven.plugin.MavenPluginManager
import org.apache.maven.plugin.MojoExecution
import org.apache.maven.project.MavenProject
import org.apache.maven.session.scope.internal.SessionScope
import org.codehaus.plexus.PlexusContainer
import org.codehaus.plexus.util.xml.Xpp3DomBuilder
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.dependency.resolution.MavenLocalRepository
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.mavenRepositories
import org.jetbrains.amper.maven.publish.createMavenExecutionRequest
import org.jetbrains.amper.maven.publish.createRepositorySession
import org.jetbrains.amper.maven.publish.mavenRepositorySystem
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.telemetry.use
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString
import kotlin.io.path.div

typealias MavenPlugin = Plugin
typealias MojoDesc = org.jetbrains.amper.frontend.plugins.AmperMavenPluginMojo

internal val PlexusContainer.mavenPluginManager: MavenPluginManager get() = lookup(MavenPluginManager::class.java)
internal val PlexusContainer.buildPluginManager get() = lookup(BuildPluginManager::class.java)
internal val PlexusContainer.sessionScope get() = lookup(SessionScope::class.java)
internal val PlexusContainer.repoSystem get() = lookup(MavenRepositorySystem::class.java)
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

    override suspend fun run(
        dependenciesResult: List<TaskResult>,
        executionContext: TaskGraphExecutionContext,
    ): TaskResult {
        val projectEmbryo = dependenciesResult.filterIsInstance<MavenPhaseResult>().single().embryo
        val localRepoPath = MavenLocalRepository.Default.repository
        val repoSession = plexus.createRepositorySession(localRepoPath)
        val request = plexus.mavenRepositorySystem.createMavenExecutionRequest(localRepoPath).apply request@{
            // Install remote artifact repositories.
            module.mavenRepositories.forEach {
                if (!it.isMavenLocal) addRemoteRepository {
                    plexus.repoSystem.createDefaultRemoteRepository(this@request).apply {
                        id = it.id
                        url = it.url

                        if (it.userName != null && it.password != null) addServer {
                            username = it.userName
                            password = it.password
                        }
                    }
                }
            }
        }
        
        val newMavenProject = MockedMavenProject(mavenProject).apply {
            file = module.source.buildFile.toFile()
            build.directory = buildOutputRoot.path.absolutePathString()
            artifact = module.asMavenArtifact("runtime")
            model.dependencyManagement = MavenDependencyManagement()
            
            // Get all collected configurations from the embryo.
            projectEmbryo.configureProject(this)
            
            // Otherwise, imports for plugins will be looked through the default maven-api
            // classloader and that will lead to the clashes.
            classRealm = plexus.containerRealm
            
            // Set artifact repositories that are usually being set within the model building listener.
            remoteArtifactRepositories = request.remoteRepositories
            
            // Set plugin repositories to the maven central.
            this@apply.pluginArtifactRepositories = listOf(plexus.repoSystem.createDefaultRemoteRepository(request))
        }

        val configDom = Xpp3DomBuilder.build(configString.reader())

        val mojoDesc = plexus.mavenPluginManager.getMojoDescriptor(
            /* plugin = */ mavenPlugin,
            /* goal = */ mojo.goal,
            /* repositories = */ newMavenProject.remotePluginRepositories,
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

        // Basically this is needed to add default parameters to the configuration.
        plexus.lifecycleExecutionPlanCalculator.setupMojoExecution(session, mavenProject, mojoExecution)

        // Execute mojo.
        spanBuilder("Executing maven plugin mojo: $goalCoordinates").use {
            // We need to enter the session, since maven creates a Guice session for
            // session scoped named beans each time a maven execution request is processed.
            plexus.sessionScope.use {
                seed(MavenSession::class.java, session)
                plexus.buildPluginManager.executeMojo(session, mojoExecution)
            }
        }

        return ModelChange(
            newMavenProject.newSourceRoots.map(::relativeToBase),
            newMavenProject.newTestSourceRoots.map(::relativeToBase),
        )
    }

    /**
     * Safe wrapper for session scope enter/exit.
     */
    private inline fun SessionScope.use(block: SessionScope.() -> Unit) {
        enter()
        try {
            block()
        } finally {
            exit()
        }
    }

    private fun relativeToBase(relative: String): Path = (module.source.moduleDir / relative).absolute()
}