/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.metadata.xml

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlBufferedReader
import nl.adaptivity.xmlutil.XmlWriter
import nl.adaptivity.xmlutil.consecutiveTextContent
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

private val xml = XML {
    defaultPolicy {
        ignoreUnknownChildren()
    }
}

internal fun String.parsePom(): Project =
    xml.decodeFromString(this.replace("<project>", "<project xmlns=\"$POM_XML_NAMESPACE\">"))

internal fun Project.serialize(): String = xml.encodeToString(this)

private const val POM_XML_NAMESPACE = "http://maven.apache.org/POM/4.0.0"

object MavenPomPropertiesXmlSerializer : KSerializer<Properties> {

    private val fallbackSerializer = MapSerializer(String.serializer(), String.serializer().nullable)

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("maven.properties") {
        element("properties", fallbackSerializer.descriptor)
    }

    private val Decoder.xmlReaderOrNull
        get() = (this as? XML.XmlInput)?.input as? XmlBufferedReader

    private val Encoder.xmlWriterOrNull
        get() = (this as? XML.XmlOutput)?.target

    private fun XmlWriter.encodeProperties(properties: Map<String, String?>) {
        startTag(POM_XML_NAMESPACE, "properties", "")
        properties.forEach { (k, v) ->
            startTag(POM_XML_NAMESPACE, k, "")
            v?.let { text(v) }
            endTag(POM_XML_NAMESPACE, k, "")
        }
        endTag(POM_XML_NAMESPACE, "properties", "")
    }

    private fun XmlBufferedReader.decodeProperties(): Map<String, String?> = buildMap {
        if (localName != "properties") throw SerializationException("Expected properties tag")
        nextTag()
        while (hasNext()) {
            if (localName == "properties" && eventType == EventType.END_ELEMENT) break
            if (eventType == EventType.START_ELEMENT) {
                if (localName == "property") {
                    var name: String? = null
                    var value: String? = null
                    while (hasNext() && !(eventType == EventType.END_ELEMENT && localName == "property")) {
                        if (eventType == EventType.START_ELEMENT) {
                            when (localName) {
                                "name" -> name = consecutiveTextContent()
                                "value" -> value = consecutiveTextContent()
                            }
                        }
                        nextTag()
                    }
                    name ?: throw SerializationException("Property name is not specified")
                    value ?: throw SerializationException("Property value for name '$name' is not specified")
                    set(name, value)
                } else {
                    val localName = localName
                    if (next() == EventType.END_ELEMENT) {
                        set(localName, null)
                    } else {
                        set(localName, consecutiveTextContent())
                    }
                }
            }
            nextTag()
        }
    }

    override fun deserialize(decoder: Decoder) = Properties(
        decoder.xmlReaderOrNull?.decodeProperties()
            ?: fallbackSerializer.deserialize(decoder)
    )

    override fun serialize(encoder: Encoder, value: Properties) =
        encoder.xmlWriterOrNull?.encodeProperties(value.properties)
            ?: fallbackSerializer.serialize(encoder, value.properties)
}

@Serializable
@XmlSerialName("project", POM_XML_NAMESPACE)
data class Project(
    @XmlElement(true)
    val modelVersion: String,
    @XmlElement(true)
    val parent: Parent? = null,
    @XmlElement(true)
    val groupId: String? = null,
    @XmlElement(true)
    val artifactId: String? = null,
    @XmlElement(true)
    val version: String? = null,
    @XmlElement(true)
    val name: String? = null,
    @XmlElement(true)
    val description: String? = null,
    @XmlElement(true)
    val url: String? = null,
    @XmlElement(true)
    val inceptionYear: String? = null,
    @XmlElement(true)
    val organization: Organization? = null,
    @XmlElement(true)
    val licenses: Licenses? = null,
    @XmlElement(true)
    val developers: Developers? = null,
    @XmlElement(true)
    val contributors: Contributors? = null,
    @XmlElement(true)
    val prerequisites: Prerequisites? = null,
    @XmlElement(true)
    val scm: Scm? = null,
    @XmlElement(true)
    val dependencyManagement: DependencyManagement? = null,
    @XmlElement(true)
    val properties: Properties? = null,
    @XmlElement(true)
    val dependencies: Dependencies? = null,
    @XmlElement(true)
    val packaging: String? = null,
)

@Serializable
@XmlSerialName("parent", POM_XML_NAMESPACE)
data class Parent(
    @XmlElement(true)
    val groupId: String,
    @XmlElement(true)
    val artifactId: String,
    @XmlElement(true)
    val version: String,
)

@Serializable
@XmlSerialName("organization", POM_XML_NAMESPACE)
data class Organization(
    @XmlElement(true)
    val name: String,
    @XmlElement(true)
    val url: String? = null,
)

@Serializable
@XmlSerialName("licenses", POM_XML_NAMESPACE)
data class Licenses(
    @XmlElement(true)
    val licenses: List<License>
)

@Serializable
@XmlSerialName("license", POM_XML_NAMESPACE)
data class License(
    @XmlElement(true)
    val name: String,
    @XmlElement(true)
    val url: String? = null,
    @XmlElement(true)
    val distribution: String? = null,
)

@Serializable
@XmlSerialName("developers", POM_XML_NAMESPACE)
data class Developers(
    @XmlElement(true)
    val developers: List<Developer>
)

@Serializable
@XmlSerialName("developer", POM_XML_NAMESPACE)
data class Developer(
    @XmlElement(true)
    val id: String? = null,
    @XmlElement(true)
    val name: String? = null,
    @XmlElement(true)
    val email: String? = null,
    @XmlElement(true)
    val organization: String? = null,
    @XmlElement(true)
    val organizationUrl: String? = null,
    @XmlElement(true)
    @XmlSerialName("roles", POM_XML_NAMESPACE)
    @XmlChildrenName("role", POM_XML_NAMESPACE)
    val roles: List<String>? = null,
)

@Serializable
@XmlSerialName("contributors", POM_XML_NAMESPACE)
data class Contributors(
    @XmlElement(true)
    val contributors: List<Contributor>
)

@Serializable
@XmlSerialName("contributor", POM_XML_NAMESPACE)
data class Contributor(
    @XmlElement(true)
    val name: String,
    @XmlElement(true)
    val organization: String? = null,
    @XmlElement(true)
    val email: String? = null,
    @XmlElement(true)
    val url: String? = null,
    @XmlElement(true)
    @XmlSerialName("roles", POM_XML_NAMESPACE)
    @XmlChildrenName("role", POM_XML_NAMESPACE)
    val roles: List<String>? = null,
)

@Serializable
@XmlSerialName("prerequisites", POM_XML_NAMESPACE)
data class Prerequisites(
    @XmlElement(true)
    val maven: String,
)

@Serializable
@XmlSerialName("dependencyManagement", POM_XML_NAMESPACE)
data class DependencyManagement(
    @XmlElement(true)
    val dependencies: Dependencies,
)

operator fun DependencyManagement?.plus(other: DependencyManagement?): DependencyManagement? {
    if (this == null) return other
    return DependencyManagement((this.dependencies + other?.dependencies)!!)
}

@Serializable(with = MavenPomPropertiesXmlSerializer::class)
@XmlSerialName("Properties", POM_XML_NAMESPACE)
data class Properties(
    val properties: Map<String, String?> = mapOf(),
)

operator fun Properties?.plus(other: Properties?): Properties? {
    if (this == null) return other
    return Properties(this.properties + (other?.properties ?: mapOf()))
}

@Serializable
@XmlSerialName("dependencies", POM_XML_NAMESPACE)
data class Dependencies(
    @XmlElement(true)
    val dependencies: List<Dependency>
)

operator fun Dependencies?.plus(other: Dependencies?): Dependencies? {
    if (this == null) return other
    return Dependencies((this.dependencies + (other?.dependencies ?: emptyList())).distinct())
}

@Serializable
@XmlSerialName("scm", POM_XML_NAMESPACE)
data class Scm(
    @XmlElement(true)
    val connection: String? = null,
    @XmlElement(true)
    val developerConnection: String? = null,
    @XmlElement(true)
    val url: String? = null,
    @XmlElement(true)
    val tag: String? = null,
)

@Serializable
@XmlSerialName("dependency", POM_XML_NAMESPACE)
data class Dependency(
    @XmlElement(true)
    val groupId: String,
    @XmlElement(true)
    val artifactId: String,
    @XmlElement(true)
    val version: String? = null,
    @XmlElement(true)
    val optional: Boolean? = null,
    @XmlElement(true)
    val type: String? = null,
    @XmlElement(true)
    val scope: String? = null,
    @XmlElement(true)
    val exclusions: Exclusions? = null,
)

@Serializable
@XmlSerialName("exclusions", POM_XML_NAMESPACE)
data class Exclusions(
    @XmlElement(true)
    val exclusions: List<Exclusion>,
)

@Serializable
@XmlSerialName("exclusion", POM_XML_NAMESPACE)
data class Exclusion(
    @XmlElement(true)
    val groupId: String,
    @XmlElement(true)
    val artifactId: String,
)

fun Dependency.expandTemplates(project: Project): Dependency = copy(
    groupId = groupId.expandTemplate(project),
    artifactId = artifactId.expandTemplate(project),
    version = version?.expandTemplate(project),
    type = type?.expandTemplate(project),
    scope = scope?.expandTemplate(project),
)

private fun String.expandTemplate(project: Project): String {
    if (!startsWith("\${") || !endsWith("}")) {
        return this
    }
    val key = removePrefix("\${").removeSuffix("}")
    if (key.startsWith("project.")) {
        val value = when (key.removePrefix("project.")) {
            "groupId" -> project.groupId
            "version" -> project.version
            else -> null
        }
        if (value != null) {
            return value
        }
    }
    return project.properties?.properties?.get(key)?.expandTemplate(project) ?: this
}
