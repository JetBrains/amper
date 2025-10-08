/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.output.HelpFormatter.ParameterHelp
import com.github.ajalt.clikt.output.MordantMarkdownHelpFormatter
import com.github.ajalt.mordant.rendering.Widget
import kotlin.collections.get

class AmperHelpFormatter(context: Context) : MordantMarkdownHelpFormatter(context, showDefaultValues = true) {

    override fun renderRepeatedMetavar(metavar: String): String {
        // make it clear that arguments should be separated by '--'
        if (metavar in setOf("[<app_arguments>]", "[<tool_arguments>]", "[<jaeger_arguments>]")) {
            return "-- ${metavar}..."
        }
        return super.renderRepeatedMetavar(metavar)
    }

    /*
    Workaround for https://github.com/ajalt/clikt/issues/617.
    This is an exact copy of super.renderOptions() but without sorting, so the order between the groups and the
    ungrouped options is determined by the position of the first ungrouped option instead of forcing the ungrouped
    options to be shown last.
    */
    override fun renderOptions(parameters: List<ParameterHelp>): List<RenderedSection<Widget>> {
        val groupsByName = parameters.filterIsInstance<ParameterHelp.Group>().associateBy { it.name }
        return parameters.filterIsInstance<ParameterHelp.Option>()
            .groupBy { it.groupName }.toList()
            .filter { it.second.isNotEmpty() }.map { (title, params) ->
                val renderedTitle = renderSectionTitle(title ?: localization.optionsTitle())
                val content = renderOptionGroup(groupsByName[title]?.help, params)
                RenderedSection(styleSectionTitle(renderedTitle), content)
            }.toList()
    }
}
