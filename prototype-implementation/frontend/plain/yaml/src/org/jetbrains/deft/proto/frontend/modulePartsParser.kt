package org.jetbrains.deft.proto.frontend

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

                val url = it.requireValue<String>("url") { "No repository url" }
                var id = it.getStringValue("id")
                if( id == null) {
                    // TODO legacy, remove after updating to 1.2.8
                    id = it.requireValue<String>("name") { "No repository id" }
                }

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
            else -> error("Unsupported repository: $it")
        }
    }

    val publicationModulePart = RepositoriesModulePart(parsedRepos)

    // Collect parts.
    return buildClassBasedSet {
        add(publicationModulePart)
    }
}