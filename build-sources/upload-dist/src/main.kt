/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("ReplacePrintlnWithLogging")

import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import org.apache.maven.model.Model
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.util.repository.AuthenticationBuilder
import org.jetbrains.amper.buildinfo.AmperBuild
import org.jetbrains.amper.dependency.resolution.LocalM2RepositoryFinder
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.maven.publish.createPlexusContainer
import org.jetbrains.amper.maven.publish.deployToRemoteRepo
import org.jetbrains.amper.maven.publish.installToMavenLocal
import org.jetbrains.amper.maven.publish.writePom
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.extension

private const val AmperGroupId = "org.jetbrains.amper"

suspend fun main(args: Array<String>) = UploadDistCommand().main(args)

class UploadDistCommand : CacheableTaskCommand() {

    private val cliRuntimeClasspath by option("--classpath").classpath().required()
    private val extraClasspaths by option("--extra-dir").namedClasspath().multiple()

    private val repository by option("--repository").choice(
        "maven-local" to Repository.MavenLocal,
        "jetbrains-team-amper" to Repository.JetBrainsTeamAmperMaven,
    ).required()

    override suspend fun ExecuteOnChangedInputs.runCached() {
        val distribution = buildDist(
            outputDir = taskOutputDirectory,
            cliRuntimeClasspath = cliRuntimeClasspath,
            extraClasspaths = extraClasspaths,
        )

        // we still publish both for backwards compatibility
        val artifacts = distribution.artifacts("amper-cli") + distribution.artifacts("cli")

        val localMavenRepoPath = LocalM2RepositoryFinder.findPath()
        val plexusContainer = createPlexusContainer()
        when (repository) {
            Repository.MavenLocal -> plexusContainer.installToMavenLocal(
                localRepositoryPath = localMavenRepoPath,
                artifacts = artifacts,
            )
            Repository.JetBrainsTeamAmperMaven -> plexusContainer.deployToRemoteRepo(
                remoteRepository = JetBrainsTeamAmperRepository,
                localRepositoryPath = localMavenRepoPath,
                artifacts = artifacts,
            )
        }
    }

    private fun Distribution.artifacts(artifactId: String): List<Artifact> {
        val wrapperArtifacts = wrappers.map { amperArtifact(artifactId, classifier = "wrapper", file = it) }
        val tarGzDistArtifact = amperArtifact(artifactId, classifier = "dist", file = cliTgz)
        // we also generate a POM file to please maven and ensure maven-metadata.xml is properly updated
        val pomArtifact = amperArtifact(artifactId, classifier = null, file = createSimplePom(artifactId, version))
        return wrapperArtifacts + tarGzDistArtifact + pomArtifact
    }

    private fun createSimplePom(artifactId: String, version: String): Path {
        val model = Model()
        model.modelVersion = "4.0.0"
        model.name = artifactId
        model.groupId = AmperGroupId
        model.artifactId = artifactId
        model.version = version
        return taskOutputDirectory.resolve("$artifactId.pom").apply { writePom(model) }
    }
}

private fun amperArtifact(artifactId: String, classifier: String?, file: Path): Artifact = DefaultArtifact(
    AmperGroupId,
    artifactId,
    classifier,
    file.extension,
    AmperBuild.mavenVersion,
).setFile(file.toFile())

private val JetBrainsTeamAmperRepository by lazy {
    val username = System.getenv("JETBRAINS_TEAM_AMPER_USERNAME")
        ?: error("JETBRAINS_TEAM_AMPER_USERNAME environment variable is not set")
    val password = System.getenv("JETBRAINS_TEAM_AMPER_PASSWORD")
        ?: error("JETBRAINS_TEAM_AMPER_PASSWORD environment variable is not set")
    val builder = RemoteRepository.Builder(
        "jetbrains-team-amper",
        "default",
        "https://packages.jetbrains.team/maven/p/amper/amper",
    )
    val authBuilder = AuthenticationBuilder()
    authBuilder.addUsername(username)
    authBuilder.addPassword(password)
    builder.setAuthentication(authBuilder.build())
    builder.build()
}

enum class Repository {
    MavenLocal,
    JetBrainsTeamAmperMaven,
}
