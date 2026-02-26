/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.maven

import com.github.ajalt.mordant.terminal.Terminal
import io.ktor.utils.io.charsets.*
import org.apache.maven.RepositoryUtils
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.doxia.site.SiteModel
import org.apache.maven.doxia.siterenderer.DocumentRenderingContext
import org.apache.maven.doxia.siterenderer.RendererException
import org.apache.maven.doxia.siterenderer.SiteRenderingContext
import org.apache.maven.doxia.siterenderer.SiteRenderingContext.SiteDirectory
import org.apache.maven.doxia.tools.SiteTool
import org.apache.maven.doxia.tools.SiteToolException
import org.apache.maven.execution.DefaultMavenExecutionResult
import org.apache.maven.execution.MavenSession
import org.apache.maven.model.Plugin
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.Mojo
import org.apache.maven.plugin.MojoExecution
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.logging.SystemStreamLog
import org.apache.maven.plugins.site.render.ReportDocumentRenderer
import org.apache.maven.project.MavenProject
import org.apache.maven.reporting.MavenReport
import org.apache.maven.reporting.exec.MavenReportExecution
import org.apache.maven.session.scope.internal.SessionScope
import org.apache.maven.shared.utils.cli.CommandLineUtils
import org.codehaus.plexus.PlexusContainer
import org.codehaus.plexus.util.xml.Xpp3DomBuilder
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.repository.RemoteRepository
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.dependency.resolution.MavenLocalRepository
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.mavenRepositories
import org.jetbrains.amper.frontend.plugins.AmperMavenPluginMojo
import org.jetbrains.amper.maven.publish.createMavenExecutionRequest
import org.jetbrains.amper.maven.publish.createRepositorySession
import org.jetbrains.amper.maven.publish.mavenRepositorySystem
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.use
import java.io.File
import java.nio.file.Path
import java.util.*
import javax.annotation.Nonnull
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.exists

typealias MavenPlugin = Plugin
typealias MojoDesc = AmperMavenPluginMojo

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
    private val pluginCoordinates = "${mavenPlugin.groupId}:${mavenPlugin.artifactId}:${mavenPlugin.version}"
    private val mavenBuildDir get() = Path(mavenProject.build.directory)

    override suspend fun run(
        dependenciesResult: List<TaskResult>,
        executionContext: TaskGraphExecutionContext,
    ): TaskResult {
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

        // We are adding only the delegate layer here to track Amper sensitive changes that
        // Maven plugin may perform. All [MavenProject] changes are applied to
        // the shared project created once for execution for each Amper module.
        val mockedMavenProject = MockedMavenProject(mavenProject).apply {
            file = module.source.buildFile.toFile()
            
            // Use a Maven-specific subdirectory to avoid clashing with Amper actions
            model.dependencyManagement = MavenDependencyManagement()

            // Set artifact repositories that are usually being set within the model building listener.
            remoteArtifactRepositories = request.remoteRepositories

            // Set plugin repositories to the maven central.
            this@apply.pluginArtifactRepositories = listOf(plexus.repoSystem.createDefaultRemoteRepository(request))
        }

        val configDom = Xpp3DomBuilder.build(configString.reader())

        val mojoDesc = plexus.mavenPluginManager.getMojoDescriptor(
            /* plugin = */ mavenPlugin,
            /* goal = */ mojo.goal,
            /* repositories = */ mockedMavenProject.remotePluginRepositories,
            /* session = */ repoSession,
        )

        val session = MavenSession(
            /* container = */ plexus,
            /* repositorySession = */ repoSession,
            /* request = */ request,
            /* result = */ DefaultMavenExecutionResult()
        ).apply { currentProject = mockedMavenProject }

        val mojoExecution = MojoExecution(
            /* mojoDescriptor = */ mojoDesc,
            /* executionId = */ executionContext.executionId,
            /* source = */ MojoExecution.Source.CLI
        ).apply { configuration = configDom }

        // Basically this is needed to add default parameters to the configuration.
        plexus.lifecycleExecutionPlanCalculator.setupMojoExecution(session, mavenProject, mojoExecution)

        // Execute mojo.
        spanBuilder("Executing maven plugin mojo: $pluginCoordinates:${mojo.goal}").use {
            // We need to enter the session, since maven creates a Guice session for
            // session scoped named beans each time a maven execution request is processed.
            plexus.sessionScope.use {
                seed(MavenSession::class.java, session)
                executeMojoOrPerformReport(
                    session = session,
                    repoSession = repoSession,
                    mojoExecution = mojoExecution,
                    remoteRepositories = request.remoteRepositories
                )
            }
        }
        
        // Since we are passing empty properties initially - we are sure that all the changes are
        // coming from mojo executions only.
        val argLine = mockedMavenProject.properties.getProperty("argLine")
        val interpolated = mockedMavenProject.interpolateArgLineWithPropertyExpressions(argLine)
        val splitArgLine = CommandLineUtils.translateCommandline(interpolated)
        
        return ModelChange(
            additionalSources = mockedMavenProject.newSourceRoots.map(::relativeToBase),
            additionalTestSources = mockedMavenProject.newTestSourceRoots.map(::relativeToBase),
            additionalTestJvmArgs = splitArgLine?.toList().orEmpty(),
        )
    }

    /**
     * Execute mojo via `executeMojo` or build a report if it is a `MavenReport` or `MavenMultiPageReport`.
     */
    private fun executeMojoOrPerformReport(
        session: MavenSession,
        repoSession: RepositorySystemSession,
        mojoExecution: MojoExecution,
        remoteRepositories: List<ArtifactRepository>,
    ) {
        // First, set appropriate class realms for descriptors (in other words, setup classloaders).
        val pluginDescriptor = mojoExecution.mojoDescriptor.pluginDescriptor
        if (pluginDescriptor.classRealm == null)
            plexus.mavenPluginManager.setupPluginRealm(pluginDescriptor, session, null, null, null)

        // Then we can access `implementationClass`, to check if it is a report mojo.
        if (MavenReport::class.java.isAssignableFrom(mojoExecution.mojoDescriptor.implementationClass))
            plexus.doReport(session, mojoExecution, repoSession, remoteRepositories)
        else
            plexus.buildPluginManager.executeMojo(session, mojoExecution)
    }

    /**
     * Render the maven report.
     */
    private fun PlexusContainer.doReport(
        session: MavenSession,
        mojoExecution: MojoExecution,
        repoSession: RepositorySystemSession,
        remoteRepositories: List<ArtifactRepository>,
    ) {
        val mojo = plexus.mavenPluginManager.getConfiguredMojo(Mojo::class.java, session, mojoExecution) as? MavenReport
            ?: error("Expected this mojo to be the instance of ${MavenReport::class.simpleName} class.")

        // Check if this mojo can generate a report for this project.
        if (!mojo.canGenerateReport()) return

        // Prepare everything for the report rendering.
        val reportExecution = MavenReportExecution(
            /* goal = */ mojoExecution.goal,
            /* plugin = */ mojoExecution.plugin,
            /* mavenReport = */ mojo,
            /* classLoader = */ mojoExecution.mojoDescriptor.realm,
            /* userDefined = */ true,
        )
        val generator =
            if (reportExecution.goal == null) null else "${reportExecution.plugin.id}:${reportExecution.goal}"
        // `outputName` is used here because it is used in `SiteMojo`. 
        // `getReportOutputDirectory` cannot be used because it needs to be set first.
        val docRenderingContext = DocumentRenderingContext(
            /* basedir = */ module.source.moduleDir.toFile(),
            /* document = */ mojo.outputName,
            /* generator = */ generator
        )
        val mavenLog = if (mojo is AbstractMojo) mojo.log else SystemStreamLog()
        val docRenderer = ReportDocumentRenderer(reportExecution, docRenderingContext, mavenLog)
        val siteRenderingContext = plexus.createSiteRenderingContext(
            locale = Locale.ROOT,
            project = mavenProject,
            repoSession = repoSession,
            remoteRepositories = remoteRepositories.map(RepositoryUtils::toRepo),
        )

        // Finally, render the report.
        val reportFile = (mavenBuildDir / "reports" / "${mojo.outputName}.html")
            .apply { parent?.createDirectories() }
            .apply { if (!exists()) createFile() }
            .toFile()
        reportFile.writer(Charset.forName(siteRenderingContext.outputEncoding)).use {
            docRenderer.renderDocument(it, siteRenderer, siteRenderingContext)
        }
    }

    private fun PlexusContainer.createSiteRenderingContext(
        locale: Locale,
        project: MavenProject,
        repoSession: RepositorySystemSession,
        remoteRepositories: List<RemoteRepository>,
    ): SiteRenderingContext {
        val siteDirectory = module.source.moduleDir.resolve("site").toFile()
        val generatedSiteDirectory = mavenBuildDir.resolve("generated-site").toFile()

        val siteModel = prepareSiteModel(locale, project, siteDirectory, repoSession, remoteRepositories)
        val templateProperties = buildMap<String, Any?> {
            this["project"] = project
            this["inputEncoding"] = Charset.defaultCharset().displayName()
            this["outputEncoding"] = Charset.defaultCharset().displayName()
            project.properties.forEach { this[it.key.toString()] = it.value }
        }

        val skinArtifact = try {
            siteTool.getSkinArtifactFromRepository(
                /* repoSession = */ repoSession,
                /* remoteProjectRepositories = */ remoteRepositories,
                /* skin = */ siteModel.skin
            )
        } catch (_: SiteToolException) {
            userReadableError("Failed to retrieve skin artifact from repository for maven goal: ${mojo.goal}")
        }

        return try {
            siteRenderer.createContextForSkin(
                /* skin = */ skinArtifact,
                /* attributes = */ templateProperties,
                /* siteModel = */ siteModel,
                /* defaultTitle = */ project.name,
                /* locale = */ locale
            )
        } catch (_: RendererException) {
            userReadableError("Failed to create context for skin for maven goal: ${mojo.goal}")
        }.apply {
            rootDirectory = project.basedir

            if (locale != SiteTool.DEFAULT_LOCALE) {
                val localeName = locale.toString()
                addSiteDirectory(SiteDirectory(siteDirectory.resolve(localeName), true))
                addSiteDirectory(SiteDirectory(generatedSiteDirectory.resolve(localeName), false))
            } else {
                addSiteDirectory(SiteDirectory(siteDirectory, true))
                addSiteDirectory(SiteDirectory(generatedSiteDirectory, false))
            }
        }
    }

    private fun PlexusContainer.prepareSiteModel(
        locale: Locale,
        project: MavenProject,
        siteDirectory: File,
        repoSession: RepositorySystemSession,
        remoteRepositories: List<RemoteRepository>,
    ): SiteModel = try {
        siteTool.getSiteModel(
            siteDirectory,
            locale,
            project,
            listOf(project),
            repoSession,
            remoteRepositories,
        )
    } catch (e: SiteToolException) {
        throw MojoExecutionException("Failed to obtain site model", e)
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

// Slightly modified copy-paste from [DefaultForkConfiguration.interpolateArgLineWithPropertyExpressions()].
// It is unfortunately private.
@Nonnull
private fun MavenProject.interpolateArgLineWithPropertyExpressions(argLine: String?): String? {
    var resolvedArgLine = argLine?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    properties.stringPropertyNames().forEach {
        val field = "@{$it}"
        if (argLine.contains(field)) {
            val replacement = properties.getProperty(it, "")
            resolvedArgLine = resolvedArgLine.replace(field, replacement)
        }
    }
    return resolvedArgLine
}