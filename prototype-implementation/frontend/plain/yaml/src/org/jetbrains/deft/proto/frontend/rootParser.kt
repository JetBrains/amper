package org.jetbrains.deft.proto.frontend

fun parseModuleParts(
    config: Settings,
): ClassBasedSet<ModelPart<*>> {
    val repos = config.getValue<List<Settings>>("repositories")
        ?: emptyList()

    // Parse repositories.
    val parsedRepos = repos.map {
        RepositoriesModelPart.Repository(
            it.requireValue<String>("name") { "No repository name" },
            it.requireValue<String>("url") { "No repository url" },
            it.getValue<String>("username"),
            it.getValue<String>("password"),
            it.getValue<Boolean>("publish") ?: false,
        )
    }

    val publicationModulePart = RepositoriesModelPart(parsedRepos)

    // Collect parts.
    return buildClassBasedSet {
        add(publicationModulePart)
    }
}