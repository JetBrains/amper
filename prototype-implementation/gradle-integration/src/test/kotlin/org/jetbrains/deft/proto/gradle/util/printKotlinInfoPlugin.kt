package org.jetbrains.deft.proto.gradle.util

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.testkit.runner.BuildResult
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

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
    return subSequence(startIndex + kotlinSourceSetInfoStartMarker.length, endIndex).toString()
}

fun parseSourceSetInfo(runResult: BuildResult): Map<String, String> {
    val sourceSetsInfo = runResult.output.extractSourceInfoOutput()
    return sourceSetsInfo.trim().split("\n").associate {
        val index = it.indexOf(":")
        it.substring(0, index) to it.substring(index + 1, it.length)
    }
}

class PrintKotlinSpecificInfoForProject : Plugin<Project> {
    override fun apply(target: Project) {
        target.tasks.create(printKotlinSourcesTask) { task ->
            task.dependsOn("build")

            task.doLast {
                val mpE = target.extensions.getByType(KotlinMultiplatformExtension::class.java)
                val sourceSets = mpE.sourceSets.joinToString(separator = "\n") { sourceSet ->
                    sourceSet.name + ":" + sourceSet.dependsOn.joinToString(separator = ";") { it.name }
                }
                println(
                    kotlinSourceSetInfoStartMarker +
                            "\n$sourceSets" +
                            "\n$kotlinSourceSetInfoEndMarker"
                )
            }
        }
    }
}