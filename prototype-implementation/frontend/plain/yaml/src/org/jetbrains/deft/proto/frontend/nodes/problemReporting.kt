package org.jetbrains.deft.proto.frontend.nodes

import org.jetbrains.deft.proto.core.messages.ProblemReporter
import org.jetbrains.deft.proto.core.messages.ProblemReporterContext
import org.jetbrains.deft.proto.frontend.BuildFileAware
import org.jetbrains.deft.proto.frontend.FrontendYamlBundle
import java.nio.file.Path
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

fun ProblemReporter.reportNodeError(
    message: String,
    node: YamlNode?,
    file: Path? = null,
) {
    if (node?.originalFile != file) {
        val fileSuffix = file?.let {
            val fileReference = buildString {
                append(file)
                node?.referencedAt?.line?.let {
                    append(":${it + 1}")
                }
            }
            "\n" + FrontendYamlBundle.message("applied.to.file", fileReference)
        } ?: ""
        reportError(
            message = message + fileSuffix,
            file = node?.originalFile ?: return,
            line = node.startMark.line + 1,
        )
    } else {
        reportError(
            message = message,
            file = file ?: node?.originalFile ?: return,
            line = node?.startMark?.line?.let { it + 1 },
        )
    }
}

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

    problemReporter.reportNodeError(
        message = FrontendYamlBundle.message(
            "wrong.element.type",
            lazyElementName(),
            nodeType<YamlNodeT>(),
            this?.nodeType ?: "null"
        ),
        node = this,
        file = file,
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
        problemReporter.reportNodeError(
            FrontendYamlBundle.message(
                "wrong.element.list.type",
                lazyElementName(),
                nodeType<ElementT>(),
                elements.firstOrNull { it !is ElementT }?.nodeType ?: "null"
            ),
            node = this,
            file = file,
        )
        return null
    }
    return elements.filterIsInstance<ElementT>()
}
