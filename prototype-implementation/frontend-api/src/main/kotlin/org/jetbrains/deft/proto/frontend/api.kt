package org.jetbrains.deft.proto.frontend

import java.net.URI


// TODO Must be replace by templates in some time!
@Deprecated("Must be replace by templates in some time!")
sealed interface ModelPart<SelfT> {
    fun default(): ModelPart<SelfT> = error("No default!")
}

// TODO Must be replace by templates in some time!
@Deprecated("Must be replace by templates in some time!")
data class PublicationModelPart(
    val mavenRepositories: List<Repository>
) : ModelPart<PublicationModelPart> {
    data class Repository(
        val name: String,
        val url: URI,
        val userName: String?,
        val password: String?,
    )
}

interface Model {
    @Deprecated("Must be replace by templates in some time!")
    val parts: ClassBasedSet<ModelPart<*>>
    val modules: List<PotatoModule>
}