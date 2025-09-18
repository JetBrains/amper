package org.jetbrains.amper.plugins.buildkonfig

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import org.jetbrains.amper.*

import java.nio.file.Path
import java.util.*
import kotlin.io.path.*

@TaskAction
fun printSources(
    @Input directory: Path,
) {
    directory.walk().forEach { file ->
        println(file.pathString)
        println(file.readText())
    }
}

@TaskAction(ExecutionAvoidance.Disabled)
fun generateKonfig(
    config: Map<String, String>,
    packageName: String,
    objectName: String,
    visibility: Visibility,
    @Output baselinePropertiesFile: Path,
    @Output outputDir: Path,
) {
    println("Generating Build Konfig...")
    val className = ClassName(
        packageName = packageName,
        objectName,
    )
    val outProperties = Properties()
    val typeSpec = TypeSpec.objectBuilder(className).apply {
        when (visibility) {
            Visibility.Internal -> addModifiers(KModifier.INTERNAL)
            Visibility.Public -> addModifiers(KModifier.PUBLIC)
        }
        config.forEach { (key, value) ->
            outProperties.setProperty(key, value)
            addProperty(
                PropertySpec.builder(key, STRING, KModifier.CONST)
                    .initializer("%S", value)
                    .build()
            )
        }
    }.build()
    baselinePropertiesFile.bufferedWriter().use { outProperties.store(it, "generated") }

    FileSpec.builder(className)
        .addType(typeSpec)
        .addFileComment("Generated using the task")
        .build()
        .writeTo(outputDir)
}
