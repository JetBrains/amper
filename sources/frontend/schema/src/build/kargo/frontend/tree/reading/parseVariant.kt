package build.kargo.frontend.tree.reading

import build.kargo.frontend.schema.BitbucketSource
import build.kargo.frontend.schema.GitHubSource
import build.kargo.frontend.schema.GitLabSource
import build.kargo.frontend.schema.GitSource
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.tree.TreeNode
import org.jetbrains.amper.frontend.tree.reading.ParsingConfig
import org.jetbrains.amper.frontend.tree.reading.YamlValue
import org.jetbrains.amper.frontend.tree.reading.parseObject
import org.jetbrains.amper.frontend.tree.reading.parseVariant
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.SchemaVariantDeclaration
import org.jetbrains.amper.problems.reporting.ProblemReporter
import kotlin.reflect.KClass

context(_: Contexts, _: ParsingConfig, reporter: ProblemReporter)
internal fun parseVariant(
    value: YamlValue,
    type: SchemaType.VariantType,
): TreeNode? = when (type.declaration.qualifiedName) {
    GitSource::class.qualifiedName -> {
        peekValueKeys(value).firstOrNull(gitSourceMappings::containsKey)?.let { key ->
            gitSourceMappings[key]?.let {
                parseObject(value, type.leafType(it))
            }
        }
    }
    else -> parseVariant(value, type)
}

private fun peekValueKeys(psi: YamlValue) = when (psi) {
    is YamlValue.Mapping -> psi.keyValues.map { it.key.psi.text }
    is YamlValue.Scalar -> listOf(psi.textValue)
    else -> emptyList()
}

private fun SchemaType.VariantType.leafType(kClass: KClass<*>): SchemaType.ObjectType =
    declaration.variantTree.first { it.declaration.qualifiedName == kClass.qualifiedName }
        .let { it as SchemaVariantDeclaration.Variant.LeafVariant }.declaration.toType()

private val gitSourceMappings = mapOf(
    "git" to GitSource::class,
    "github" to GitHubSource::class,
    "bitbucket" to BitbucketSource::class,
    "gitlab" to GitLabSource::class,
)
