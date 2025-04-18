/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Config
import org.eclipse.jgit.storage.file.FileBasedConfig
import org.eclipse.jgit.util.FS
import org.eclipse.jgit.util.SystemReader
import java.security.MessageDigest
import kotlin.use

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.eclipse.jgit:org.eclipse.jgit:7.1.0.202411261347-r")
    }
}

val generateBuildProperties by tasks.creating(WriteProperties::class.java) {
    destinationFile.set(project.layout.buildDirectory.file("build.properties"))

    comment = "Build info"
    property("version", project.version)

    // no git directory in bootstrap test
    if (project.findProperty("inBootstrapMode") != "true") {
        val gitRoot = rootProject.projectDir.resolve(".git")
        if (gitRoot.exists()) {
            // This is to avoid issues with people who use config parameters that are not supported by JGit.
            // For example, the 'patience' diff algorithm isn't supported.
            runWithoutGlobalGitConfig {
                Git.open(rootProject.projectDir).use { git ->
                    val repo = git.repository
                    val head = repo.getReflogReader("HEAD").lastEntry
                    val shortHash = repo.newObjectReader().use { it.abbreviate(head.newId).name() }
                    property("commitHash", head.newId.name)
                    property("commitShortHash", shortHash)
                    property("commitDate", head.who.whenAsInstant)

                    // When developing locally, we want to somehow capture changes to the local sources because we want to
                    // invalidate incremental state files based on this. If we don't, changing some Amper code will not
                    // cause state invalidation, and some tasks will be marked up-to-date even though their code has changed
                    // and they would produce a different output.
                    // Using the git index for this is insufficient because it only captures the paths but not the contents
                    // of the files. That's why we use a digest of the whole diff.
                    val localDiffHash = git.diff().call().map { it.newId.toObjectId().name }.hash()
                    property("localChangesHash", localDiffHash)
                }
            }
        }
    }
}

private fun Iterable<String>.hash(): String {
    val hasher = MessageDigest.getInstance("md5")
    forEach {
        hasher.update(it.encodeToByteArray())
    }
    return hasher.digest().joinToString("") { "%02x".format(it) }
}

tasks.jvmProcessResources {
    dependsOn(generateBuildProperties)
    from(generateBuildProperties)
}

private fun runWithoutGlobalGitConfig(work: () -> Unit) {
    val oldSystemReader = SystemReader.getInstance()
    try {
        SystemReader.setInstance(EmptyConfigSystemReader(oldSystemReader))
        work()
    } finally {
        SystemReader.setInstance(oldSystemReader)
    }
}

private class EmptyConfigSystemReader(private val delegate: SystemReader = getInstance()) : SystemReader() {
    override fun getHostname(): String? = delegate.hostname
    override fun getenv(variable: String?): String? = delegate.getenv(variable)
    override fun getProperty(key: String?): String? = delegate.getProperty(key)
    override fun getCurrentTime(): Long = delegate.currentTime
    override fun getTimezone(`when`: Long): Int = delegate.getTimezone(`when`)
    override fun openUserConfig(parent: Config?, fs: FS?): FileBasedConfig = NoopFileBasedConfig(parent, fs)
    override fun openSystemConfig(parent: Config?, fs: FS?): FileBasedConfig = NoopFileBasedConfig(parent, fs)
    override fun openJGitConfig(parent: Config?, fs: FS?): FileBasedConfig = delegate.openJGitConfig(parent, fs)
}

private class NoopFileBasedConfig(parent: Config?, fs: FS?) : FileBasedConfig(parent, null, fs) {
    override fun load() {}
    override fun isOutdated(): Boolean = false
}
