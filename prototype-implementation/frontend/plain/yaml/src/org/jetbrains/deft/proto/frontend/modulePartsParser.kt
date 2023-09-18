package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.nodes.*
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.reader

context(BuildFileAware)
fun parseModuleParts(
    config: YamlNode.Mapping,
): ClassBasedSet<ModulePart<*>> {
    // Collect parts.
    return buildClassBasedSet {
        add(parseRepositories(config))
        add(parseMetaSettings(config))
    }
}

context(BuildFileAware)
private fun parseMetaSettings(config: YamlNode.Mapping): MetaModulePart {
    val meta = config.getMappingValue("pot")
        ?: return MetaModulePart() // TODO Check for type.

    fun parseLayout(): Layout? {
        val metaNode = meta["layout"]
        if (metaNode !is YamlNode.Scalar?) {
            parseError("Layout is not scalar: $metaNode")
        }
        return when (metaNode?.value) {
            null -> return null
            "default" -> Layout.DEFT
            "gradle-kmp" -> Layout.GRADLE
            "gradle-jvm" -> Layout.GRADLE_JVM
            else -> parseError("Unknown layout: ${metaNode?.value}")
        }
    }

    return MetaModulePart(
        parseLayout() ?: Layout.DEFT
    )
}

context(BuildFileAware)
private fun parseRepositories(config: YamlNode.Mapping): RepositoriesModulePart {
    val repos = config.getSequenceValue("repositories") ?: emptyList()

    // Parse repositories.
    val parsedRepos = repos.map {
        when (it) {
            is YamlNode.Scalar -> {
                RepositoriesModulePart.Repository(it.value, it.value)
            }

            is YamlNode.Mapping -> {
                val url = it.getStringValue("url") ?: parseError("No repository url")
                val id = it.getStringValue("id") ?: url
                val shouldPublish = it.getBooleanValue("publish") ?: false

                val credentials = it.getMappingValue("credentials")
                if (credentials == null) {
                    RepositoriesModulePart.Repository(id, url, shouldPublish)
                } else {
                    val credentialsPath = credentials.getStringValue("file")
                        ?: parseError("Must specify credentials file in credentials section.")
                    val userNameKey = credentials.getStringValue("usernameKey")
                        ?: parseError("Must specify \"usernameKey\" in credentials section.")
                    val passwordKey = credentials.getStringValue("passwordKey")
                        ?: parseError("Must specify \"passwordKey\" in credentials section.")

                    val credentialsRelative = buildFile.parent.resolve(credentialsPath).normalize()
                    val credentialsFile = credentialsRelative.takeIf { it.exists() }
                        ?: parseError("Credentials file $credentialsRelative does not exist.")
                    val credentialProperties = Properties().apply { load(credentialsFile.reader()) }

                    val userName = credentialProperties.getProperty(userNameKey)
                        ?: parseError("Has no key \"$it\" in passed file: $credentialsPath.")
                    val password = credentialProperties.getProperty(passwordKey)
                        ?: parseError("Has no key \"$it\" in passed file: $credentialsPath.")

                    RepositoriesModulePart.Repository(
                        id = id,
                        url = url,
                        publish = shouldPublish,
                        userName = userName,
                        password = password,
                    )
                }
            }

            else -> parseError("Unsupported repository: $it")
        }
    }

    return RepositoriesModulePart(parsedRepos)
}

