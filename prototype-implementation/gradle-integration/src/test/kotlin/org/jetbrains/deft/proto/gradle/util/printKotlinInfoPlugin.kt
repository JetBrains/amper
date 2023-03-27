package org.jetbrains.deft.proto.gradle.util

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.LanguageSettingsBuilder

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
    val startIndex = indexOf(kotlinSourceSetInfoStartMarker)
    val endIndex = indexOf(kotlinSourceSetInfoEndMarker)
    return subSequence(startIndex + kotlinSourceSetInfoStartMarker.length, endIndex).toString().trim()
}

class PrintKotlinSpecificInfoForProject : Plugin<Project> {
    override fun apply(target: Project) {
        target.tasks.create(printKotlinSourcesTask) { task ->
            task.doLast {
                val mpE = target.extensions.getByType(KotlinMultiplatformExtension::class.java)
                val namePad = mpE.sourceSets.namePad()
                val sourceSets = mpE.sourceSets.joinToString(separator = "\n") { it.pretty(namePad) }
                println(
                    kotlinSourceSetInfoStartMarker +
                            "\n$sourceSets" +
                            "\n$kotlinSourceSetInfoEndMarker"
                )
            }
        }
    }

    private fun Iterable<KotlinSourceSet>.namePad() = maxOf { it.name.length }

    private fun KotlinSourceSet.pretty(namePad: Int = 32) =
        name.padEnd(namePad) + ":" +
                "depends(${dependsOn.joinToString(separator = ",") { it.name }.padEnd(16)})" +
                " " +
                "sourceDirs(${
                    this.kotlin
                        .srcDirs
                        .map { it.parentFile.name }
                        .toSet()
                        .joinToString(separator = ",") { it }
                        .padEnd(16)
                })" +
                " " +
                "lang(${languageSettings.pretty().padEnd(32)})"

    private fun LanguageSettingsBuilder.pretty() =
        "api=" + apiVersion +
                " version=" + languageVersion +
                " progressive=" + progressiveMode +
                " features=" + enabledLanguageFeatures.joinToString(separator = ",") { it }
}