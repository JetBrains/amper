/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.wrapper

internal class Template(
    val text: String,
    val name: String,
)

internal fun interface TemplateProvider {
    fun getTemplate(name: String): Template?
}

internal fun Template.substitute(
    macroSubstitutions: Map<String, String>,
    templateProvider: TemplateProvider,
) : String {
    return text.replace(MacroRegex) { match ->
        val macro = match.groupValues[1]
        if (':' in macro) {
            check(macro.startsWith("include:", ignoreCase = true)) {
                "invalid macro directive: `${match.value}`; only `@include:<name>@` is supported"
            }
            val name = macro.removePrefix("include:")
            val nestedTemplate = checkNotNull(templateProvider.getTemplate(name)) {
                "`$name` not found (included at char range ${match.range} in `$name`)"
            }
            // TODO: Handle recursion/deduplication?
            nestedTemplate.substitute(macroSubstitutions, templateProvider)
        } else {
            checkNotNull(macroSubstitutions[macro]) {
                "macro `$macro` is not defined (requested at char range ${match.range} in `$name`)"
            }
        }
    }
}

private val MacroRegex = """@(\S+)@""".toRegex()
