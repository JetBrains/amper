/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.frontend.Platform
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
        Context {
            cache = {
                localRepositories = listOf(GradleLocalRepository(path))
            }
        }.use { context ->
            val dependency =
                MavenDependency(context.settings.fileCache, "org.jetbrains.kotlin", "kotlin-test", "1.9.20")
            val extension = "module"
            val name = "${getNameWithoutExtension(dependency)}.$extension"
            val target = path.resolve(
                "${dependency.group}/${dependency.module}/${dependency.version}/3bf4b49eb37b4aca302f99bd99769f6e310bdb2/$name"
            )
            Files.createDirectories(target.parent)
            Path.of("testData/metadata/json/module/$name").copyTo(target)

            val dependencyFile = DependencyFile(dependency, getNameWithoutExtension(dependency), extension)
            assertTrue(runBlocking { dependencyFile.getPath()!!.startsWith(path) })

            val downloaded = runBlocking { dependencyFile.isDownloaded() }
            val hasMatchingChecksum = runBlocking { dependencyFile.hasMatchingChecksum(ResolutionLevel.LOCAL, context) }
            assertTrue(dependency.messages.isEmpty(), "There must be no messages: ${dependency.messages}")
            assertTrue(downloaded, "File must be downloaded as it was created above")
            assertTrue(hasMatchingChecksum, "File must have matching checksum as it was created above")
        }
    }

    @Test
    fun `jackson-module-kotlin-2_15_2_jar hash`() {
        val path = temp.toPath()
        Context {
            cache = {
                localRepositories = listOf(MavenLocalRepository(path))
            }
        }.use { context ->
            val dependency = MavenDependency(
                context.settings.fileCache,
                "com.fasterxml.jackson.module", "jackson-module-kotlin", "2.15.2"
            )
            runBlocking {
                dependency.resolveChildren(context, ResolutionLevel.NETWORK)
                dependency.downloadDependencies(context)
            }
            val messages = dependency.messages.filter { "Downloaded from" !in it.text }
            assertTrue(messages.isEmpty(), "There must be no messages for $dependency: $messages")
        }
    }

    @Test
    fun `org_jetbrains_kotlinx kotlinx-datetime 0_5_0 with extra slash`() {
        val path = temp.toPath()
        Context {
            platform = setOf(Platform.MACOS_X64)
            repositories = listOf("https://repo.maven.apache.org/maven2/")
            cache = {
                localRepositories = listOf(MavenLocalRepository(path))
            }
        }.use { context ->
            val dependency = MavenDependency(
                context.settings.fileCache,
                "org.jetbrains.kotlinx", "kotlinx-datetime", "0.5.0"
            )
            runBlocking {
                dependency.resolveChildren(context, ResolutionLevel.NETWORK)
            }
            val errors = dependency.messages.filter { it.severity == Severity.ERROR }
            assertTrue(errors.isEmpty(), "There must be no errors: $errors")
        }
    }

    @Test
    fun `org_jetbrains_kotlinx kotlinx_coroutines_core 1_7_3`() {
        val path = temp.toPath()
        Context {
            platform = setOf(Platform.JVM, Platform.ANDROID)
            repositories = listOf("https://repo.maven.apache.org/maven2/")
            cache = {
                localRepositories = listOf(MavenLocalRepository(path))
            }
        }.use { context ->
            val dependency = MavenDependency(
                context.settings.fileCache,
                "org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.7.3"
            )
            runBlocking {
                dependency.resolveChildren(context, ResolutionLevel.NETWORK)
            }
            val errors = dependency.messages.filter { it.severity == Severity.ERROR }
            assertTrue(errors.isEmpty(), "There must be no errors: $errors")


            val dependencyFile = DependencyFile(dependency, getNameWithoutExtension(dependency), "jar")
            assertTrue(runBlocking { dependencyFile.getPath()!!.startsWith(path) })

            val downloaded = runBlocking { dependencyFile.isDownloaded() }
            val hasMatchingChecksum = runBlocking { dependencyFile.hasMatchingChecksum(ResolutionLevel.LOCAL, context) }
            assertTrue(downloaded, "File must be downloaded as it was created above")
            assertTrue(hasMatchingChecksum, "File must have matching checksum as it was created above")
        }
    }

    @Test
    fun `org_jetbrains_kotlinx kotlinx_datetime 0_4_0`() {
        val path = temp.toPath()
        Context {
            platform = setOf(Platform.JVM, Platform.ANDROID)
            repositories = listOf("https://repo.maven.apache.org/maven2/")
            cache = {
                localRepositories = listOf(MavenLocalRepository(path))
            }
        }.use { context ->
            val dependency = MavenDependency(
                context.settings.fileCache,
                "org.jetbrains.kotlinx", "kotlinx-datetime", "0.4.0"
            )
            runBlocking {
                dependency.resolveChildren(context, ResolutionLevel.NETWORK)
            }
            val errors = dependency.messages.filter { it.severity == Severity.ERROR }
            assertTrue(errors.isEmpty(), "There must be no errors: $errors")


            val dependencyFile = DependencyFile(dependency, "${getNameWithoutExtension(dependency)}-all", "jar")
            assertTrue(runBlocking { dependencyFile.getPath()!!.startsWith(path) })

            val downloaded = runBlocking { dependencyFile.isDownloaded() }
            val hasMatchingChecksum = runBlocking { dependencyFile.hasMatchingChecksum(ResolutionLevel.LOCAL, context) }
            assertTrue(downloaded, "File must be downloaded as it was created above")
            assertTrue(hasMatchingChecksum, "File must have matching checksum as it was created above")
        }
    }

//    @Test
    fun `org_jetbrains_kotlinx kotlinx-datetime 0_4_0 empty module file`() {
        val path = temp.toPath()
        Context {
            platform = setOf(Platform.MACOS_ARM64)
            repositories = listOf("https://fake-repository/")
            cache = {
                localRepositories = listOf(MavenLocalRepository(path))
            }
        }.use { context ->
            val dependency = MavenDependency(
                context.settings.fileCache,
                "org.jetbrains.kotlinx", "kotlinx-datetime", "0.4.0"
            )
            runBlocking {
                dependency.resolveChildren(context, ResolutionLevel.NETWORK)
            }
            val errors = dependency.messages.filter { it.severity == Severity.ERROR }
//            assertTrue(errors.isEmpty(), "There must be no errors: $errors")

            val dependencyFile = DependencyFile(dependency, getNameWithoutExtension(dependency), "module")
            val downloaded = runBlocking { dependencyFile.isDownloaded() }
            val hasMatchingChecksum = runBlocking { dependencyFile.hasMatchingChecksum(ResolutionLevel.LOCAL, context) }
            assertTrue(runBlocking { dependencyFile.getPath()!!.startsWith(path) })
            assertTrue(downloaded, "File must be downloaded as it was created above")
            assertTrue(hasMatchingChecksum, "File must have matching checksum as it was created above")
        }
    }
}
