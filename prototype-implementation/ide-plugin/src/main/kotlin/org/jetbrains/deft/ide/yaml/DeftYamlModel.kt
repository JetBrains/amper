@file:Suppress("UnstableApiUsage")

package org.jetbrains.deft.ide.yaml

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiReference
import org.jetbrains.deft.ide.DeftPotReference
import org.jetbrains.yaml.meta.model.*
import org.jetbrains.yaml.psi.YAMLScalar
import java.util.regex.Pattern

internal class DeftYamlModel(isTemplate: Boolean) : YamlMetaClass("deft-root") {
    init {
        addFeature(Field("dependencies", DeftDependenciesListMetaType())
            .withNamePattern(Pattern.compile("^(test-)?dependencies(@.+)?\$")))
        addFeature(Field("apply", DeftTemplatesListMetaType()))
        if (!isTemplate) {
            addFeature(Field("product", DeftProductMetaType()))
        }
    }

    private class DeftDependencyMetaType : YamlReferenceType("deft-dependency") {
        override fun getReferencesFromValue(valueScalar: YAMLScalar): Array<PsiReference> {
            return arrayOf(DeftPotReference(valueScalar, false))
        }
    }

    private class DeftTemplateMetaType : YamlReferenceType("deft-template") {
        override fun getReferencesFromValue(valueScalar: YAMLScalar): Array<PsiReference> {
            return arrayOf(DeftPotReference(valueScalar, true))
        }
    }

    private class DeftDependenciesListMetaType : YamlArrayType(DeftDependencyMetaType())

    private class DeftTemplatesListMetaType : YamlArrayType(DeftTemplateMetaType())

    private class DeftProductMetaType : YamlStringType() {
        override fun getValueLookups(
            insertedScalar: YAMLScalar,
            completionContext: CompletionContext?
        ): List<LookupElement> {
            return listOf(
                LookupElementBuilder.create("lib")
                    .withInsertHandler { context, _ ->
                        val toInsert = """
                            |
                            |  type: lib
                            |  platforms:
                            |    - 
                        """.trimMargin()

                        context.document.replaceString(context.startOffset, context.tailOffset, toInsert)
                        context.editor.caretModel.moveToOffset(context.startOffset + toInsert.length)
                    }
            )
        }
    }
}
