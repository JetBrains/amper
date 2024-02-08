/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.test.assertTrue

class DependencyFileTest {

    @field:TempDir
    lateinit var temp: File

    @Test
    fun `kotlin-test-1_9_20 module hash`() {
        val path = temp.toPath()
        val settings = Context {
            cache = {
                localRepositories = listOf(GradleLocalRepository(path))
            }
        }.settings
        val dependency = MavenDependency(settings.fileCache, "org.jetbrains.kotlin", "kotlin-test", "1.9.20")
        val extension = "module"
        val name = "${getNameWithoutExtension(dependency)}.$extension"
        val target = path.resolve(
            "${dependency.group}/${dependency.module}/${dependency.version}/3bf4b49eb37b4aca302f99bd99769f6e310bdb2/$name"
        )
        Files.createDirectories(target.parent)
        Path.of("testData/metadata/json/$name").copyTo(target)

        val dependencyFile = DependencyFile(dependency, getNameWithoutExtension(dependency), extension)
        assertTrue(dependencyFile.path!!.startsWith(path))

        val downloaded = dependencyFile.isDownloaded()
        val hasMatchingChecksum = dependencyFile.hasMatchingChecksum(ResolutionLevel.LOCAL, settings)
        assertTrue(dependency.messages.isEmpty(), "There must be no messages: ${dependency.messages}")
        assertTrue(downloaded, "File must be downloaded as it was created above")
        assertTrue(hasMatchingChecksum, "File must have matching checksum as it was created above")
    }

    @Test
    fun `jackson-module-kotlin-2_15_2_jar hash`() {
        val path = temp.toPath()
        val context = Context {
            cache = {
                localRepositories = listOf(MavenLocalRepository(path))
            }
        }
        val dependency = MavenDependency(
            context.settings.fileCache,
            "com.fasterxml.jackson.module", "jackson-module-kotlin", "2.15.2"
        )
        dependency.resolve(context, ResolutionLevel.NETWORK)
        dependency.downloadDependencies(context.settings)
        val errors = dependency.messages.filter { it.severity == Severity.ERROR }
        assertTrue(errors.isEmpty(), "There must be no errors: $errors")
    }
}
