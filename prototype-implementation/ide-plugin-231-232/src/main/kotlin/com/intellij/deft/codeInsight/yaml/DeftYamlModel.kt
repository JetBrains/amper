@file:Suppress("UnstableApiUsage")

package com.intellij.deft.codeInsight.yaml

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.deft.codeInsight.DeftPotReference
import com.intellij.deft.codeInsight.DeftProduct
import com.intellij.psi.PsiReference
import org.jetbrains.yaml.meta.model.*
import org.jetbrains.yaml.psi.YAMLScalar

internal class DeftYamlModel(product: DeftProduct?, isTemplate: Boolean) : YamlMetaClass("deft-root") {

  init {
    addFeature(Field("dependencies", DeftDependenciesListMetaType(product, isTest = false)))
    addFeature(Field("test-dependencies", DeftDependenciesListMetaType(product, isTest = true)))
    product?.platforms?.forEach {
      addFeature(Field("dependencies@$it", DeftDependenciesListMetaType(product, isTest = false)))
      addFeature(Field("test-dependencies@$it", DeftDependenciesListMetaType(product, isTest = true)))
    }
    addFeature(Field("settings", DeftSettingsListMetaType()))
    addFeature(Field("test-settings", DeftSettingsListMetaType()))
    product?.platforms?.forEach {
      addFeature(Field("settings@$it", DeftSettingsListMetaType()))
      addFeature(Field("test-settings@$it", DeftSettingsListMetaType()))
    }
    addFeature(Field("apply", DeftTemplatesListMetaType()))
    if (!isTemplate) {
      addFeature(Field("product", DeftProductMetaType()))
    }
  }

  private class DeftTemplateMetaType : YamlReferenceType("deft-template") {
    override fun getReferencesFromValue(valueScalar: YAMLScalar): Array<PsiReference> {
      return arrayOf(DeftPotReference(valueScalar, true))
    }
  }

  internal class DeftDependenciesListMetaType(product: DeftProduct?, isTest: Boolean)
    : YamlArrayType(DeftDependencyMetaType(product, isTest))

  internal class DeftSettingsListMetaType : YamlStringType() // TODO: to be extended within DEFT-21

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
