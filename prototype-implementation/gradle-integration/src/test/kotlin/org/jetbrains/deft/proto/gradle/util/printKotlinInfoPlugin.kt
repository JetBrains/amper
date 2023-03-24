package org.jetbrains.deft.proto.gradle.util

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.testkit.runner.BuildResult
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
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
            task.dependsOn("build")

            task.doLast {
                val mpE = target.extensions.getByType(KotlinMultiplatformExtension::class.java)
                val sourceSets = mpE.sourceSets.joinToString(separator = "\n") { sourceSet ->
                    sourceSet.name + ":" +
                            "depends " + sourceSet.dependsOn.joinToString(separator = "_") { it.name } +
                            ",lang " + sourceSet.languageSettings.pretty()
                }
                println(
                    kotlinSourceSetInfoStartMarker +
                            "\n$sourceSets" +
                            "\n$kotlinSourceSetInfoEndMarker"
                )
            }
        }
    }

    fun LanguageSettingsBuilder.pretty() =
        "api_" + apiVersion +
                "_version_" + languageVersion +
                "_progressive_" + progressiveMode +
                "_features_" + enabledLanguageFeatures.joinToString(separator = "+") { it }
}