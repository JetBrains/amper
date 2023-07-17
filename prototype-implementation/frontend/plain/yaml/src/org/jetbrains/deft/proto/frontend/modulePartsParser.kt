package org.jetbrains.deft.proto.frontend

fun parseModuleParts(
    config: Settings,
): ClassBasedSet<ModulePart<*>> {
    val repos = config.getValue<List<Settings>>("repositories")
        ?: emptyList()

    // Parse repositories.
    val parsedRepos = repos.map {
        RepositoriesModulePart.Repository(
            it.requireValue<String>("name") { "No repository name" },
            it.requireValue<String>("url") { "No repository url" },
            it.getStringValue("username"),
            it.getStringValue("password"),
            it.getValue<Boolean>("publish") ?: false,
        )
    }

    val publicationModulePart = RepositoriesModulePart(parsedRepos)

    // Collect parts.
    return buildClassBasedSet {
        add(publicationModulePart)
    }
}