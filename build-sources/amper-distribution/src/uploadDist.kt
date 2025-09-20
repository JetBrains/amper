/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.apache.maven.model.Model
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.util.repository.AuthenticationBuilder
import org.jetbrains.amper.Input
import org.jetbrains.amper.TaskAction
import org.jetbrains.amper.buildinfo.AmperBuild
import org.jetbrains.amper.dependency.resolution.LocalM2RepositoryFinder
import org.jetbrains.amper.maven.publish.createPlexusContainer
import org.jetbrains.amper.maven.publish.deployToRemoteRepo
import org.jetbrains.amper.maven.publish.installToMavenLocal
import org.jetbrains.amper.maven.publish.writePom
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries

@TaskAction
fun uploadDist(
    @Input distributionDir: Path,
    @Input wrappersDir: Path,
    repository: Repository,
) {
    val distribution = Distribution(
        version = AmperBuild.mavenVersion,
        cliTgz = distributionDir / "cli.tgz",
        wrappers = wrappersDir.listDirectoryEntries(),
    )

    // we still publish both for backwards compatibility
    val tempDirectory = createTempDirectory() // TODO: Expose such facility via Amper
    try {
        val artifacts = context(tempDirectory) {
            distribution.artifacts("amper-cli") + distribution.artifacts("cli")
        }

        val localMavenRepoPath = LocalM2RepositoryFinder.findPath()
        val plexusContainer = createPlexusContainer(Distribution::class.java.classLoader)
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
    } finally {
        tempDirectory.deleteRecursively()
    }
}

context(tempDirectory: Path)
private fun Distribution.artifacts(artifactId: String): List<Artifact> {
    val wrapperArtifacts = wrappers.map { amperArtifact(artifactId, classifier = "wrapper", file = it) }
    val tarGzDistArtifact = amperArtifact(artifactId, classifier = "dist", file = cliTgz)
    // we also generate a POM file to please maven and ensure maven-metadata.xml is properly updated
    val pomArtifact = amperArtifact(artifactId, classifier = null, file = createSimplePom(artifactId, version))
    return wrapperArtifacts + tarGzDistArtifact + pomArtifact
}

context(tempDirectory: Path)
private fun createSimplePom(artifactId: String, version: String): Path {
    val model = Model()
    model.modelVersion = "4.0.0"
    model.name = artifactId
    model.groupId = AmperGroupId
    model.artifactId = artifactId
    model.version = version
    return tempDirectory.resolve("$artifactId.pom").apply { writePom(model) }
}

private fun amperArtifact(artifactId: String, classifier: String?, file: Path): Artifact = DefaultArtifact(
    AmperGroupId,
    artifactId,
    classifier,
    file.extension,
    AmperBuild.mavenVersion,
).setFile(file.toFile())

private const val AmperGroupId = "org.jetbrains.amper"

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

private data class Distribution(
    val version: String,
    val cliTgz: Path,
    val wrappers: List<Path>,
)
