package com.example.amper.plugin.consume_sources

import org.jetbrains.amper.Input
import org.jetbrains.amper.ModuleSources
import org.jetbrains.amper.Schema
import org.jetbrains.amper.TaskAction

import kotlin.io.path.*

@Schema
interface Settings {
    val sources: ModuleSources
}

@TaskAction
fun consume(
    @Input settings: Settings,
) {
    println("Consuming sources: ${settings.sources.sourceDirectories.size}")
    for (path in settings.sources.sourceDirectories) {
        val files = if (path.isDirectory()) {
            path.walk().filter { it.isRegularFile() }.map { it.name }.toList()
        } else null
        println("Got source path: $path - ${files}")
    }
}