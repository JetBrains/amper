package org.jetbrains.deft.proto.gradle.util

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.LanguageSettingsBuilder
import java.io.File
import java.nio.file.Path

/**
 * A simple plugin to print out information
 * about kotlin source sets, compilations, etc.
 */
@Suppress("unused")
class PrintKotlinSpecificInfo : Plugin<Settings> {
    override fun apply(target: Settings) {
        target.gradle.beforeProject {
            it.plugins.apply(PrintKotlinSpecificInfoForProject::class.java)
        }
    }
}

const val printKotlinSourcesTask = "printKotlinSources"
private const val kotlinSourceSetInfoStartMarker = "KOTLIN SOURCE SET INFO START"
private const val kotlinSourceSetInfoEndMarker = "KOTLIN SOURCE SET INFO END"

fun String.extractSourceInfoOutput(): String {
    var (result, offset) = extractNextSourceInfoOutput()
    while (offset != -1) {
        val (extracted, nextOffset) = extractNextSourceInfoOutput(offset)
        offset = nextOffset
        result = "$result\n$extracted"
    }
    return result.trim()
}

private fun String.extractNextSourceInfoOutput(offset: Int = 0): Pair<String, Int> {
    val startIndex = indexOf(kotlinSourceSetInfoStartMarker, startIndex = offset)
    val endIndex = indexOf(kotlinSourceSetInfoEndMarker, startIndex = offset)
    if (startIndex == -1 || endIndex == -1) return "" to -1
    val extracted = subSequence(startIndex + kotlinSourceSetInfoStartMarker.length, endIndex).toString().trim()
    val nextOffset = endIndex + kotlinSourceSetInfoEndMarker.length
    return extracted to nextOffset
}

class PrintKotlinSpecificInfoForProject : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.create(printKotlinSourcesTask) { task ->
            task.doLast {
                val mpE = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return@doLast
                val sourceSets = mpE.sourceSets.joinToString(separator = "\n") { it.pretty(project) }
                println(
                    kotlinSourceSetInfoStartMarker +
                            "\n${project.path}" +
                            "\n$sourceSets" +
                            "\n$kotlinSourceSetInfoEndMarker"
                )
            }
        }
    }

    private fun relative(path1: File, path2: File): String {
        val basePath: Path = path1.toPath().normalize().toAbsolutePath()
        val targetPath: Path = path2.toPath().normalize().toAbsolutePath()
        val relativePath: Path = basePath.relativize(targetPath)

        return relativePath.toString()
    }



    private fun KotlinSourceSet.pretty(project: Project): String {
        return "  $name:" +
                "\n   depends(${dependsOn.joinToString(separator = ",") { it.name }})" +
                "\n   sourceDirs(${
                    this.kotlin
                        .srcDirs
                        .map { relative(project.projectDir, it) }
                        .toSet()
                        .joinToString(separator = ",") { it }
                })" +
                "\n   lang(${languageSettings.pretty()})" +
                "\n   implDeps(${declaredImplementationDependencies(project)})"
    }

    private fun KotlinSourceSet.declaredImplementationDependencies(project: Project): String {
        val conf = project.configurations.findByName(implementationConfigurationName) ?: return ""
        return conf.allDependencies.joinToString(separator = ",") { "${it.group}:${it.name}:${it.version}" }
    }

    private fun LanguageSettingsBuilder.pretty() =
        "api=" + apiVersion +
                " version=" + languageVersion +
                " progressive=" + progressiveMode +
                " features=" + enabledLanguageFeatures.joinToString(separator = ",") { it }
}