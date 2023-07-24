package org.jetbrains.deft.proto.frontend

context(BuildFileAware)
fun parseModuleParts(
    config: Settings,
): ClassBasedSet<ModulePart<*>> {
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
                    it.getValue<Boolean>("publish") ?: false,
                )
            }
            else -> parseError("Unsupported repository: $it")
        }
    }

    val publicationModulePart = RepositoriesModulePart(parsedRepos)

    // Collect parts.
    return buildClassBasedSet {
        add(publicationModulePart)
    }
}