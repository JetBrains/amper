package org.jetbrains.amper.plugins.buildkonfig

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import org.jetbrains.amper.*

import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.walk

@TaskAction
fun printSources(
    @Input directory: Path,
    // @Inject logger: TaskLogger,
) {
    directory.walk().forEach { file ->
        println(file.pathString)
        println(file.readText())
    }
}

@TaskAction
fun generateKonfig(
    config: Schema1,
    @Output outputDir: Path,
) {
    val className = ClassName(
        packageName = config.packageName,
        config.objectName,
    )
    val typeSpec = TypeSpec.objectBuilder(className).apply {
        when (config.visibility ?: Visibility.public) {
            Visibility.internal -> addModifiers(KModifier.INTERNAL)
            Visibility.public -> addModifiers(KModifier.PUBLIC)
        }
        config.config.forEach { (key, value) ->
            addProperty(
                PropertySpec.builder(key, STRING, KModifier.CONST)
                    .initializer("%S", value)
                    .build()
            )
        }
    }.build()

    FileSpec.builder(className)
        .addType(typeSpec)
        .addFileComment("Generated using the task")
        .build()
        .writeTo(outputDir)
}
