package org.jetbrains.deft.proto.frontend.nodes

import org.jetbrains.deft.proto.core.messages.ProblemReporterContext
import org.jetbrains.deft.proto.frontend.BuildFileAware
import org.jetbrains.deft.proto.frontend.FrontendYamlBundle
import java.nio.file.Path
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

inline fun <reified YamlNodeT : YamlNode?> nodeType(): String = when (YamlNodeT::class) {
    YamlNode.Scalar::class -> "string"
    YamlNode.Sequence::class -> "list"
    YamlNode.Mapping::class -> "map"
    else -> YamlNodeT::class.simpleName ?: "null"
}

context(BuildFileAware, ProblemReporterContext)
@OptIn(ExperimentalContracts::class)
inline fun <reified YamlNodeT : YamlNode?> YamlNode?.castOrReport(lazyElementName: () -> String): Boolean {
    contract {
        returns(true) implies (this@castOrReport is YamlNodeT)
    }

    return castOrReport<YamlNodeT>(
        buildFile,
        lazyElementName
    )
}

context(ProblemReporterContext)
@OptIn(ExperimentalContracts::class)
inline fun <reified YamlNodeT : YamlNode?> YamlNode?.castOrReport(
    file: Path,
    lazyElementName: () -> String,
): Boolean {
    contract {
        returns(true) implies (this@castOrReport is YamlNodeT)
    }

    if (this is YamlNodeT) return true

    problemReporter.reportError(
        FrontendYamlBundle.message(
            "wrong.element.type",
            lazyElementName(),
            nodeType<YamlNodeT>(),
            this?.nodeType ?: "null"
        ),
        file = file,
        line = (this?.startMark?.line ?: 0) + 1
    )
    return false
}

context(BuildFileAware, ProblemReporterContext)
inline fun <reified ElementT : YamlNode> YamlNode.Sequence.getListOfElementsOrNull(lazyElementName: () -> String): List<ElementT>? =
    getListOfElementsOrNull(buildFile, lazyElementName)

context(ProblemReporterContext)
inline fun <reified ElementT : YamlNode> YamlNode.Sequence.getListOfElementsOrNull(
    file: Path,
    lazyElementName: () -> String,
): List<ElementT>? {
    if (elements.any { it !is ElementT }) {
        problemReporter.reportError(
            FrontendYamlBundle.message(
                "wrong.element.list.type",
                lazyElementName(),
                nodeType<ElementT>(),
                elements.firstOrNull { it !is ElementT }?.nodeType ?: "null"
            ),
            file = file,
            line = (startMark.line) + 1
        )
        return null
    }
    return elements.filterIsInstance<ElementT>()
}
