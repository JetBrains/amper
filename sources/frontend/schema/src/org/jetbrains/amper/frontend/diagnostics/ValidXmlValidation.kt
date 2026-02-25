/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.contexts.MinimalModule
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.tree.StringNode
import org.jetbrains.amper.frontend.tree.TreeNode
import org.jetbrains.amper.frontend.tree.visitMappingNodes
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.ProblemReporter
import java.io.StringReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader

object ValidXmlValidation : TreeDiagnosticFactory {

    override fun analyze(root: TreeNode, minimalModule: MinimalModule, problemReporter: ProblemReporter) {
        root.visitMappingNodes { node ->
            node.children
                .filter { (it.propertyDeclaration?.type as? SchemaType.StringType)?.semantics == SchemaType.StringType.Semantics.MavenPlexusConfigXml }
                .mapNotNull { it.value as? StringNode }
                .filter { it.value.isInvalidXml() }
                .forEach {
                    problemReporter.reportBundleError(
                        source = it.trace.asBuildProblemSource(),
                        messageKey = "validation.maven.invalid.plexus.configuration.xml",
                        diagnosticId = FrontendDiagnosticId.InvalidXmlForPlexusConfiguration,
                    )
                }
        }
    }

    /**
     * Naive method to check if the string is valid XML, using [XMLStreamReader].
     */
    private fun String.isInvalidXml(): Boolean {
        try {
            val xmlReader = XMLInputFactory.newFactory().createXMLStreamReader(StringReader(this))
            while (xmlReader.hasNext()) xmlReader.next()
            return false
        } catch (_: XMLStreamException) {
            return true
        }
    }
}