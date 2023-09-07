package org.jetbrains.deft.proto.frontend

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
    val meta = config.getValue<Settings>("deft") ?: return MetaModulePart() // TODO Check for type.

    fun parseLayout(): Layout? {
        return when (val layoutValue = meta["layout"]) {
            null -> return null
            !is String -> parseError("Layout value is not string: $layoutValue")
            "deft" -> Layout.DEFT
            "gradle" -> Layout.GRADLE
            "gradle-jvm" -> Layout.GRADLE_JVM
            else -> parseError("Unknown layout: $layoutValue")
        }
    }

    return MetaModulePart(
        parseLayout() ?: Layout.DEFT
    )
}

context(BuildFileAware)
private fun parseRepositories(config: Settings): RepositoriesModulePart {
    val repos = config.getValue<List<Any>>("repositories")
        ?: emptyList()

    // Parse repositories.
    val parsedRepos = repos.map {
        when (it) {
            is String -> {
                RepositoriesModulePart.Repository(
                    it,
                    it,
                    null,
                    null,
                    false,
                )
            }

            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                it as Settings

                val url = it.getStringValue("url") ?: parseError("No repository url")
                val id = it.getStringValue("id") ?: parseError("No repository id")

                val userName = it.getStringValue("username")
                val password = it.getStringValue("password")
                RepositoriesModulePart.Repository(
                    id,
                    url,
                    userName,
                    password,
                    it.getBooleanValue("publish") ?: false,
                )
            }
            else -> parseError("Unsupported repository: $it")
        }
    }

    return RepositoriesModulePart(parsedRepos)
}

