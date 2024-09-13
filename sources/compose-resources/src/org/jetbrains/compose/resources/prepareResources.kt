/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.compose.resources

import org.w3c.dom.Node
import org.xml.sax.SAXParseException
import java.io.File
import java.nio.file.Path
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Converts .xml resources in "[originalResourcesDir]/values/" into the binary format.
 * Other files are copied "as is" into the [outputDirectory].
 *
 * @param qualifier a source set specific string to be used in the converted resources file names
 *  internally
 * @param originalResourcesDir the user resources directory root (e.g. `composeResources/`)
 * @param outputDirectory the output directory for the prepared resources
 */
fun prepareResources(
    qualifier: String,
    originalResourcesDir: Path,
    outputDirectory: Path,
) {
    // NOTE: This was two operations in the compose-multiplatform plugin, see:
    // - XmlValuesConverterTask       (1) for xml conversion
    // - CopyNonXmlValueResourcesTask (2) for other files copying
    // - PrepareComposeResourcesTask   - just depends on (1) and (2) to aggregate in terms of Gradle.
    // Here I've decided to unite these steps into one step for simplicity.

    // TODO: do it async?
    originalResourcesDir.toFile().listNotHiddenFiles().forEach { file ->
        if (file.isDirectory && file.name.startsWith("values")) {
            file.listNotHiddenFiles().forEach { f ->
                if (f.extension.equals("xml", true)) {
                    val output = outputDirectory.toFile()
                        .resolve(f.parentFile.name)
                        .resolve(f.nameWithoutExtension + ".$qualifier.$CONVERTED_RESOURCE_EXT")
                    output.parentFile.mkdirs()
                    try {
                        convert(f, output)
                    } catch (e: SAXParseException) {
                        error("XML file ${f.absolutePath} is not valid. Check the file content.")
                    } catch (e: Exception) {
                        error("XML file ${f.absolutePath} is not valid. ${e.message}")
                    }
                } else {
                    // TODO: What to do with non-xml files that lay in `values` dir?
                }
            }
        } else {
            file.copyRecursively(
                target = outputDirectory.toFile(),
            )
        }
    }
}

internal data class ValueResourceRecord(
    val type: ResourceType,
    val key: String,
    val content: String
) {
    fun getAsString(): String {
        return listOf(type.typeName, key, content).joinToString(SEPARATOR)
    }

    companion object {
        private const val SEPARATOR = "|"
        fun createFromString(string: String): ValueResourceRecord {
            val parts = string.split(SEPARATOR)
            return ValueResourceRecord(
                ResourceType.fromString(parts[0])!!,
                parts[1],
                parts[2]
            )
        }
    }
}

private fun convert(original: File, converted: File) {
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(original)
    val items = doc.getElementsByTagName("resources").item(0).childNodes
    val records = List(items.length) { items.item(it) }
        .filter { it.hasAttributes() }
        .map { getItemRecord(it) }

    //check there are no duplicates type + key
    records.groupBy { it.key }
        .filter { it.value.size > 1 }
        .forEach { (key, records) ->
            val allTypes = records.map { it.type }
            require(allTypes.size == allTypes.toSet().size) { "Duplicated key '$key'." }
        }

    val fileContent = buildString {
        appendLine("version:$FORMAT_VERSION")
        records.map { it.getAsString() }.sorted().forEach { appendLine(it) }
    }
    converted.writeText(fileContent)
}

private fun getItemRecord(node: Node): ValueResourceRecord {
    val type = ResourceType.fromString(node.nodeName) ?: error("Unknown resource type: '${node.nodeName}'.")
    val key = node.attributes.getNamedItem("name")?.nodeValue ?: error("Attribute 'name' not found.")
    val value: String
    when (type) {
        ResourceType.STRING -> {
            val content = handleSpecialCharacters(node.textContent)
            value = content.asBase64()
        }

        ResourceType.STRING_ARRAY -> {
            val children = node.childNodes
            value = List(children.length) { children.item(it) }
                .filter { it.nodeName == "item" }
                .joinToString(",") { child ->
                    val content = handleSpecialCharacters(child.textContent)
                    content.asBase64()
                }
        }

        ResourceType.PLURAL_STRING -> {
            val children = node.childNodes
            value = List(children.length) { children.item(it) }
                .filter { it.nodeName == "item" }
                .joinToString(",") { child ->
                    val content = handleSpecialCharacters(child.textContent)
                    val quantity = child.attributes.getNamedItem("quantity").nodeValue
                    quantity.uppercase() + ":" + content.asBase64()
                }
        }

        else -> error("Unknown string resource type: '$type'.")
    }
    return ValueResourceRecord(type, key, value)
}

private fun String.asBase64() =
    Base64.getEncoder().encode(this.encodeToByteArray()).decodeToString()

//https://developer.android.com/guide/topics/resources/string-resource#escaping_quotes
/**
 * Replaces
 *
 * '\n' -> new line
 *
 * '\t' -> tab
 *
 * '\uXXXX' -> unicode symbol
 *
 * '\\' -> '\'
 *
 * @param string The input string to handle.
 * @return The string with special characters replaced according to the logic.
 */
private fun handleSpecialCharacters(string: String): String {
    val unicodeNewLineTabRegex = Regex("""\\u[a-fA-F\d]{4}|\\n|\\t""")
    val doubleSlashRegex = Regex("""\\\\""")
    val doubleSlashIndexes = doubleSlashRegex.findAll(string).map { it.range.first }
    val handledString = unicodeNewLineTabRegex.replace(string) { matchResult ->
        if (doubleSlashIndexes.contains(matchResult.range.first - 1)) matchResult.value
        else when (matchResult.value) {
            "\\n" -> "\n"
            "\\t" -> "\t"
            else -> matchResult.value.substring(2).toInt(16).toChar().toString()
        }
    }.replace("""\\""", """\""")
    return handledString
}

internal const val CONVERTED_RESOURCE_EXT = "cvr" //Compose Value Resource
private const val FORMAT_VERSION = 0
