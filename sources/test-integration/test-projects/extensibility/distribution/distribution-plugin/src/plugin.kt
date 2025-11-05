package com.example.distribution


import org.jetbrains.amper.plugins.*
import kotlin.concurrent.thread
import kotlin.io.path.*
import java.nio.file.Path

@Configurable
interface DistributionSettings {
    val extraNamedClasspaths: Map<String, Classpath>
    val extraDependency: Dependency?
}

@TaskAction
fun buildDistribution(
    @Output distributionDir: Path,
    @Input baseJar: CompilationArtifact,
    @Input baseClasspath: Classpath,
    @Input settings: DistributionSettings,
) {
    distributionDir.createDirectories()
    println("Hello from distribution")
    printClasspathInfo("base", baseClasspath)
    settings.extraNamedClasspaths.forEach { (name, classpath) ->
        printClasspathInfo(name, classpath)
    }
    val t = thread {
        println("compilation result: ${baseJar}")
        val t2 = thread {
            println("compilation result path: ${baseJar.artifact}")
        }
        t2.join()
    }
    t.join()
}

private fun printClasspathInfo(name: String, classpath: Classpath) {
    println("classpath $name.dependencies = ${classpath.dependencies}")
    classpath.dependencies.forEachIndexed { index, it ->
        println("classpath $name.dependencies[$index] = ${it}")
        when(it) {
            is Dependency.Maven -> println("classpath $name.dependencies[$index].coordinates = ${it.coordinates}")
            is Dependency.Local -> println("classpath $name.dependencies[$index].modulePath = ${it.modulePath}")
        }
    }
    println("classpath $name.resolvedFiles = ${classpath.resolvedFiles}")
}