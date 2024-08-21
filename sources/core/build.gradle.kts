/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.eclipse.jgit.api.Git
import java.security.MessageDigest

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.eclipse.jgit:org.eclipse.jgit:6.7.0.202309050840-r")
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
            val git = Git.open(gitRoot)
            val repo = git.repository
            val head = repo.getReflogReader("HEAD").lastEntry
            val shortHash = repo.newObjectReader().use { it.abbreviate(head.newId).name() }
            property("commitHash", head.newId.name)
            property("commitShortHash", shortHash)
            property("commitDate", head.who.`when`.toInstant())

            // When developing locally, we want to somehow capture changes to the local sources because we want to
            // invalidate incremental state files based on this. Using the git index for this is insufficient because
            // it only captures the paths but not the contents of the files.
            // That's why we use a digest of the whole diff.
            val localDiffHash = git.diff().call().map { it.newId.toObjectId().name }.hash()
            property("localChangesHash", localDiffHash)
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
