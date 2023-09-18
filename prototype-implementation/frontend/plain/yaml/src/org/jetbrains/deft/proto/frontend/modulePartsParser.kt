package org.jetbrains.deft.proto.frontend

import java.util.Properties
import kotlin.io.path.exists
import kotlin.io.path.reader

context(BuildFileAware)
fun parseModuleParts(
    config: Settings,
): ClassBasedSet<ModulePart<*>> {
    // Collect parts.
    return buildClassBasedSet {
        add(parseRepositories(config))
        add(parseMetaSettings(config))
    }
}

context(BuildFileAware)
private fun parseMetaSettings(config: Settings): MetaModulePart {
    val meta = config.getValue<Settings>("pot")
        ?: return MetaModulePart() // TODO Check for type.

    fun parseLayout(): Layout? {
        return when (val layoutValue = meta["layout"]) {
            null -> return null
            !is String -> parseError("Layout value is not string: $layoutValue")
            "default" -> Layout.DEFT
            "gradle-kmp" -> Layout.GRADLE
            "gradle-jvm" -> Layout.GRADLE_JVM
            else -> parseError("Unknown layout: $layoutValue")
        }
    }

    return MetaModulePart(
        parseLayout() ?: Layout.DEFT
    )
}

val repositoriesKey = SettingsKey<List<Any>>("repositories")
val credentialsKey = SettingsKey<Settings>("credentials")

context(BuildFileAware)
private fun parseRepositories(config: Settings): RepositoriesModulePart {
    val repos = config[repositoriesKey] ?: emptyList()

    // Parse repositories.
    val parsedRepos = repos.map {
        when (it) {
            is String -> {
                RepositoriesModulePart.Repository(it, it)
            }

            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                it as Settings

                val url = it.getStringValue("url") ?: parseError("No repository url")
                val id = it.getStringValue("id") ?: url
                val shouldPublish = it.getBooleanValue("publish") ?: false

                val credentials = it[credentialsKey]
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

