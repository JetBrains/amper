package com.intellij.deft.codeInsight.yaml

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.deft.codeInsight.DeftPlatformReference
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.meta.impl.YamlMetaTypeCompletionProviderBase
import org.jetbrains.yaml.meta.impl.YamlMetaTypeProvider

@Suppress("UnstableApiUsage")
internal class DeftCompletionProvider : YamlMetaTypeCompletionProviderBase() {
  override fun getMetaTypeProvider(params: CompletionParameters): YamlMetaTypeProvider {
    return DeftMetaTypeProvider.getInstance()
  }

  override fun addCompletions(params: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    if (params.position.containingFile.findReferenceAt(params.offset) is DeftPlatformReference) return
    super.addCompletions(params, context, result)
  }
}
