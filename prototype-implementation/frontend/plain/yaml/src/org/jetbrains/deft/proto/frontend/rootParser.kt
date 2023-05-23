package org.jetbrains.deft.proto.frontend

import org.yaml.snakeyaml.Yaml
import java.io.InputStream
import java.net.URI


fun parseModuleParts(value: InputStream): ClassBasedSet<ModelPart<*>> {
    // Parse yaml.
    val yaml = Yaml()
    val config = yaml.load<Settings>(value)

    val repos = config.getValue<Settings>("repositories") as? List<Settings>
        ?: emptyList()

    // Parse repositories.
    val parsedRepos = repos.map {
        PublicationModelPart.Repository(
            it.requireValue<String>("name") { "No repository name" },
            URI.create(it.requireValue<String>("url") { "No repository url" }),
            it.getValue<String>("name"),
            it.getValue<String>("name"),
        )
    }
    val publicationModulePart = PublicationModelPart(parsedRepos)

    // Collect parts.
    return buildClassBasedSet {
        add(publicationModulePart)
    }
}