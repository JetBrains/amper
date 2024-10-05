import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import org.jetbrains.amper.dependency.resolution.metadata.xml.xml

internal fun String.parseSettings(): Settings = xml.decodeFromString(this)

internal fun Settings.serialize(): String = xml.encodeToString(this)

@Serializable
@XmlSerialName("settings", "http://maven.apache.org/SETTINGS/1.0.0")
internal data class Settings(
    @XmlElement(true)
    val localRepository: String,
)

private val PROPERTY_REF_REGEX = Regex("""\$\{([^}]+?)}""")

private fun String.substituteProperties(): String = replace(PROPERTY_REF_REGEX) { match ->
    val varName = match.groupValues[1]
    if (varName.startsWith("env.")) {
        System.getenv(varName.removePrefix("env.")) ?: ""
    } else {
        System.getProperty(varName) ?: ""
    }
}

internal fun Settings.localRepository() = localRepository.substituteProperties()