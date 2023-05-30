package org.jetbrains.deft.proto.frontend

import org.yaml.snakeyaml.Yaml
import java.io.InputStream
import java.net.URI


fun parseModuleParts(
    values: InputStream,
    localProps: InputStream?,
): ClassBasedSet<ModelPart<*>> {
    // Parse yaml.
    val yaml = Yaml()
    val config = yaml.load<Settings>(values)

    val repos = config.getValue<List<Settings>>("repositories")
        ?: emptyList()

    // Parse repositories.
    val parsedRepos = with(localProps.toInterpolateCtx()) {
        repos.map {
            PublicationModelPart.Repository(
                it.requireValue<String>("name") { "No repository name" },
                URI.create(it.requireValue<String>("url") { "No repository url" }),
                it.getValue<String>("username")?.tryInterpolate(),
                it.getValue<String>("password")?.tryInterpolate(),
            )
        }
    }
    val publicationModulePart = PublicationModelPart(parsedRepos)

    // Collect parts.
    return buildClassBasedSet {
        add(publicationModulePart)
    }
}