package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.core.Result
import org.jetbrains.deft.proto.core.deftFailure
import org.jetbrains.deft.proto.core.messages.ProblemReporterContext
import org.jetbrains.deft.proto.frontend.nodes.YamlNode
import org.jetbrains.deft.proto.frontend.nodes.castOrReport
import org.jetbrains.deft.proto.frontend.nodes.get

data class Variant(
    val dimension: String,
    val options: List<Option>,
) {
    data class Option(
        val name: String,
        val dependsOn: List<Dependency>,
        /**
         * Used to propagate naming correctly for fragments while multiplying.
         */
        val isDefaultOption: Boolean = false,
        /**
         * Used to determine which fragment is built by default.
         */
        val isDefaultFragment: Boolean = false,
    )

    data class Dependency(
        val target: String,
        val isFriend: Boolean = false,
    )
}

private val mainVariant = Variant(
    "mode", options = listOf(
        Variant.Option(
            name = "main",
            dependsOn = emptyList(),
            isDefaultOption = true,
            isDefaultFragment = true,
        ),
        Variant.Option(
            name = "test",
            dependsOn = listOf(
                Variant.Dependency(
                    target = "main",
                    isFriend = true,
                )
            ),
        )
    )
)

context(BuildFileAware, ProblemReporterContext)
fun getVariants(config: YamlNode.Mapping): Result<List<Variant>> {
    val variantsNode = config["variants"] ?: YamlNode.Sequence.Empty
    if (!variantsNode.castOrReport<YamlNode.Sequence> { FrontendYamlBundle.message("element.name.variants.list") }) {
        return deftFailure()
    }
    if (variantsNode.elements.isEmpty()) {
        return Result.success(listOf(mainVariant))
    }
    val initialVariants: List<List<String>> = when (variantsNode.elements[0]) {
        is YamlNode.Scalar -> {
            if (!variantsNode.elements.all { it is YamlNode.Scalar }) {
                problemReporter.reportError(
                    FrontendYamlBundle.message("incorrect.variants.format"),
                    file = buildFile,
                    line = variantsNode.startMark.line
                )
                return deftFailure()
            }
            listOf(variantsNode.elements.filterIsInstance<YamlNode.Scalar>().map { it.value })
        }

        is YamlNode.Sequence -> {
            if (!variantsNode.elements.all { dimension -> dimension is YamlNode.Sequence && dimension.elements.all { it is YamlNode.Scalar } }) {
                problemReporter.reportError(
                    FrontendYamlBundle.message("incorrect.variants.format"),
                    file = buildFile,
                    line = variantsNode.startMark.line
                )
                return deftFailure()
            }
            variantsNode.elements
                .filterIsInstance<YamlNode.Sequence>()
                .map { dimension -> dimension.elements.filterIsInstance<YamlNode.Scalar>().map { it.value } }
        }

        else -> {
            problemReporter.reportError(
                FrontendYamlBundle.message("incorrect.variants.format"),
                file = buildFile,
                line = variantsNode.startMark.line
            )
            return deftFailure()
        }
    }
    var i = 0
    return Result.success(initialVariants.map {
        val dimension = "dimension${++i}"
        Variant(dimension, options = it.mapIndexed { index, optionName ->
            Variant.Option(
                name = optionName,
                dependsOn = listOf(Variant.Dependency(dimension)),
                isDefaultFragment = index == 0,
            )
        } + Variant.Option(
            name = dimension,
            dependsOn = emptyList(),
            isDefaultOption = true,
        ))
    } + mainVariant)
}

val List<Variant>.dimensionVariants: Set<String>
    get() = asSequence().flatMap { it.options }.filter { it.isDefaultOption }.map { it.name }.toSet()
val List<Variant>.defaultVariants: Set<String>
    get() = asSequence().filter { false }.flatMap { it.options }.map { it.name }.toSet()
