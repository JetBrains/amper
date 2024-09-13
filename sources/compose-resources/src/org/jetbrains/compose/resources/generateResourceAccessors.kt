/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.compose.resources

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MUTABLE_MAP
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.withIndent
import java.io.File
import java.nio.file.Path
import java.util.*
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.relativeTo

/**
 * Generates resource accessors as extension methods to the objects inside the `Res` class.
 *
 * This is intended to be generated into every platform-specific source set.
 *
 * Depends on the results of [prepareResources].
 *
 * @param packageName kotlin package name to generate the code in.
 * @param makeAccessorsPublic if `true` then the code is public, `internal` otherwise.
 * @param qualifier a source set specific string to be used in the converted resources file names
 *  internally. Must be consistent with the value passed to [prepareResources].
 * @param packagingDir is a relative path for resource packaging to be used to load resources at runtime.
 * @param preparedResourcesDirectory the output directory of the [prepareResources] step.
 * @param outputSourceDirectory the generated source directory; the package directories hierarchy will be created in the
 *  directory if needed.
 */
fun generateResourceAccessors(
    packageName: String,
    qualifier: String,
    packagingDir: String,
    makeAccessorsPublic: Boolean,
    preparedResourcesDirectory: Path,
    outputSourceDirectory: Path,
) {
    val dirs = preparedResourcesDirectory.toFile().listNotHiddenFiles()

    dirs.forEach { f ->
        if (!f.isDirectory) {
            error("${f.name} is not directory! Raw files should be placed in " +
                    "'${preparedResourcesDirectory.name}/files' directory.")
        }
    }

    //type -> id -> resource item
    val resources: Map<ResourceType, Map<String, List<ResourceItem>>> = dirs
        .flatMap { dir ->
            dir.listNotHiddenFiles()
                .mapNotNull { it.fileToResourceItems(preparedResourcesDirectory) }
                .flatten()
        }
        .groupBy { it.type }
        .mapValues { (_, items) -> items.groupBy { it.name } }

    getAccessorsSpecs(
        resources = resources,
        packageName = packageName,
        sourceSetName = qualifier,
        moduleDir = packagingDir,
        isPublic = makeAccessorsPublic,
    ).forEach { it.writeTo(outputSourceDirectory) }
}

// We need to divide accessors by different files because
//
// if all accessors are generated in a single object
// then a build may fail with: org.jetbrains.org.objectweb.asm.MethodTooLargeException: Method too large: Res$drawable.<clinit> ()V
// e.g. https://github.com/JetBrains/compose-multiplatform/issues/4285
//
// if accessor initializers are extracted from the single object but located in the same file
// then a build may fail with: org.jetbrains.org.objectweb.asm.ClassTooLargeException: Class too large: Res$drawable
private const val ITEMS_PER_FILE_LIMIT = 500

private fun getAccessorsSpecs(
    //type -> id -> items
    resources: Map<ResourceType, Map<String, List<ResourceItem>>>,
    packageName: String,
    sourceSetName: String,
    moduleDir: String,
    isPublic: Boolean
): List<FileSpec> {
    val resModifier = if (isPublic) KModifier.PUBLIC else KModifier.INTERNAL
    val files = mutableListOf<FileSpec>()

    //we need to sort it to generate the same code on different platforms
    sortResources(resources).forEach { (type, idToResources) ->
        val chunks = idToResources.keys.chunked(ITEMS_PER_FILE_LIMIT)

        chunks.forEachIndexed { index, ids ->
            files.add(
                getChunkFileSpec(
                    type,
                    "${type.accessorName.uppercaseFirstChar()}$index.$sourceSetName",
                    sourceSetName.uppercaseFirstChar() + type.accessorName.uppercaseFirstChar() + index,
                    packageName,
                    moduleDir,
                    resModifier,
                    idToResources.subMap(ids.first(), true, ids.last(), true)
                )
            )
        }
    }

    return files
}

private fun getChunkFileSpec(
    type: ResourceType,
    fileName: String,
    chunkClassName: String,
    packageName: String,
    moduleDir: String,
    resModifier: KModifier,
    idToResources: Map<String, List<ResourceItem>>
): FileSpec {
    return FileSpec.builder(packageName, fileName).also { chunkFile ->
        chunkFile.addAnnotation(
            AnnotationSpec.builder(ClassName("kotlin", "OptIn"))
                .addMember("org.jetbrains.compose.resources.InternalResourceApi::class")
                .build()
        )

        val objectSpec = TypeSpec.objectBuilder(chunkClassName).also { typeObject ->
            typeObject.addModifiers(KModifier.PRIVATE)
            val properties = idToResources.keys.map { resName ->
                PropertySpec.builder(resName, type.getClassName())
                    .delegate("\nlazy·{ %N() }", "init_$resName")
                    .build()
            }
            typeObject.addProperties(properties)
        }.build()
        chunkFile.addType(objectSpec)

        //__collect${chunkClassName}Resources function
        chunkFile.addFunction(
            FunSpec.builder("_collect${chunkClassName}Resources")
                .addAnnotation(internalAnnotation)
                .addModifiers(KModifier.INTERNAL)
                .addParameter(
                    "map",
                    MUTABLE_MAP.parameterizedBy(String::class.asClassName(), type.getClassName())
                )
                .also { collectFun ->
                    idToResources.keys.forEach { resName ->
                        collectFun.addStatement("map.put(%S, $chunkClassName.%N)", resName, resName)
                    }
                }
                .build()
        )

        idToResources.forEach { (resName, items) ->
            val accessor = PropertySpec.builder(resName, type.getClassName(), resModifier)
                .receiver(ClassName(packageName, "Res", type.accessorName))
                .getter(FunSpec.getterBuilder().addStatement("return $chunkClassName.%N", resName).build())
                .build()
            chunkFile.addProperty(accessor)

            val initializer = FunSpec.builder("init_$resName")
                .addModifiers(KModifier.PRIVATE)
                .returns(type.getClassName())
                .addStatement(
                    CodeBlock.builder()
                        .add("return %T(\n", type.getClassName()).withIndent {
                            add("%S,", "$type:$resName")
                            if (type.requiresKeyName()) add(" %S,", resName)
                            withIndent {
                                add("\nsetOf(\n").withIndent {
                                    items.forEach { item ->
                                        add("%T(", resourceItemClass)
                                        add("setOf(").addQualifiers(item).add("), ")
                                        //file separator should be '/' on all platforms
                                        add("\"$moduleDir${item.path.invariantSeparatorsPathString}\", ")
                                        add("${item.offset}, ${item.size}")
                                        add("),\n")
                                    }
                                }
                                add(")\n")
                            }
                        }
                        .add(")")
                        .build().toString()
                )
                .build()
            chunkFile.addFunction(initializer)
        }
    }.build()
}

private fun File.fileToResourceItems(
    relativeTo: Path
): List<ResourceItem>? {
    val file = this
    val dirName = file.parentFile.name ?: return null
    val typeAndQualifiers = dirName.split("-")
    if (typeAndQualifiers.isEmpty()) return null

    val typeString = typeAndQualifiers.first().lowercase()
    val qualifiers = typeAndQualifiers.takeLast(typeAndQualifiers.size - 1)
    val path = file.toPath().relativeTo(relativeTo)


    if (typeString == "string") {
        error("Forbidden directory name '$dirName'! String resources should be declared in 'values/strings.xml'.")
    }

    if (typeString == "files") {
        if (qualifiers.isNotEmpty()) error("The 'files' directory doesn't support qualifiers: '$dirName'.")
        return null
    }

    if (typeString == "values" && file.extension.equals(CONVERTED_RESOURCE_EXT, true)) {
        return getValueResourceItems(file, qualifiers, path)
    }

    val type = ResourceType.fromString(typeString) ?: error("Unknown resource type: '$typeString'.")
    return listOf(ResourceItem(type, qualifiers, file.nameWithoutExtension.asUnderscoredIdentifier(), path))
}

private fun getValueResourceItems(dataFile: File, qualifiers: List<String>, path: Path): List<ResourceItem> {
    val result = mutableListOf<ResourceItem>()
    dataFile.bufferedReader().use { f ->
        var offset = 0L
        var line: String? = f.readLine()
        while (line != null) {
            val size = line.encodeToByteArray().size

            //first line is meta info
            if (offset > 0) {
                result.add(getValueResourceItem(line, offset, size.toLong(), qualifiers, path))
            }

            offset += size + 1 // "+1" for newline character
            line = f.readLine()
        }
    }
    return result
}

private fun getValueResourceItem(
    recordString: String,
    offset: Long,
    size: Long,
    qualifiers: List<String>,
    path: Path
): ResourceItem {
    val record = ValueResourceRecord.createFromString(recordString)
    return ResourceItem(record.type, qualifiers, record.key.asUnderscoredIdentifier(), path, offset, size)
}

private fun sortResources(
    resources: Map<ResourceType, Map<String, List<ResourceItem>>>
): TreeMap<ResourceType, TreeMap<String, List<ResourceItem>>> {
    val result = TreeMap<ResourceType, TreeMap<String, List<ResourceItem>>>()
    resources
        .entries
        .forEach { (type, items) ->
            val typeResult = TreeMap<String, List<ResourceItem>>()
            items
                .entries
                .forEach { (name, resItems) ->
                    typeResult[name] = resItems.sortedBy { it.path }
                }
            result[type] = typeResult
        }
    return result
}

private fun ResourceType.requiresKeyName() =
    this in setOf(ResourceType.STRING, ResourceType.STRING_ARRAY, ResourceType.PLURAL_STRING)

private val resourceItemClass = ClassName("org.jetbrains.compose.resources", "ResourceItem")

private val internalAnnotation = AnnotationSpec.builder(
    ClassName("org.jetbrains.compose.resources", "InternalResourceApi")
).build()

private fun CodeBlock.Builder.addQualifiers(resourceItem: ResourceItem): CodeBlock.Builder {
    val languageQualifier = ClassName("org.jetbrains.compose.resources", "LanguageQualifier")
    val regionQualifier = ClassName("org.jetbrains.compose.resources", "RegionQualifier")
    val themeQualifier = ClassName("org.jetbrains.compose.resources", "ThemeQualifier")
    val densityQualifier = ClassName("org.jetbrains.compose.resources", "DensityQualifier")

    val languageRegex = Regex("[a-z]{2,3}")
    val regionRegex = Regex("r[A-Z]{2}")

    val qualifiersMap = mutableMapOf<ClassName, String>()

    fun saveQualifier(className: ClassName, qualifier: String) {
        qualifiersMap[className]?.let {
            error("${resourceItem.path} contains repetitive qualifiers: '$it' and '$qualifier'.")
        }
        qualifiersMap[className] = qualifier
    }

    resourceItem.qualifiers.forEach { q ->
        when (q) {
            "light",
            "dark" -> {
                saveQualifier(themeQualifier, q)
            }

            "mdpi",
            "hdpi",
            "xhdpi",
            "xxhdpi",
            "xxxhdpi",
            "ldpi" -> {
                saveQualifier(densityQualifier, q)
            }

            else -> when {
                q.matches(languageRegex) -> {
                    saveQualifier(languageQualifier, q)
                }

                q.matches(regionRegex) -> {
                    saveQualifier(regionQualifier, q)
                }

                else -> error("${resourceItem.path} contains unknown qualifier: '$q'.")
            }
        }
    }
    qualifiersMap[themeQualifier]?.let { q -> add("%T.${q.uppercase()}, ", themeQualifier) }
    qualifiersMap[densityQualifier]?.let { q -> add("%T.${q.uppercase()}, ", densityQualifier) }
    qualifiersMap[languageQualifier]?.let { q -> add("%T(\"$q\"), ", languageQualifier) }
    qualifiersMap[regionQualifier]?.let { q ->
        val lang = qualifiersMap[languageQualifier]
        if (lang == null) {
            error("Region qualifier must be used only with language.\nFile: ${resourceItem.path}")
        }
        val langAndRegion = "$lang-$q"
        if (!resourceItem.path.toString().contains("-$langAndRegion")) {
            error("Region qualifier must be declared after language: '$langAndRegion'.\nFile: ${resourceItem.path}")
        }
        add("%T(\"${q.takeLast(2)}\"), ", regionQualifier)
    }

    return this
}
