package org.jetbrains.deft.ide.yaml

import com.intellij.codeInsight.completion.CompletionParameters
import org.jetbrains.yaml.meta.impl.YamlMetaTypeCompletionProviderBase
import org.jetbrains.yaml.meta.impl.YamlMetaTypeProvider

@Suppress("UnstableApiUsage")
internal class DeftCompletionProvider : YamlMetaTypeCompletionProviderBase() {
    override fun getMetaTypeProvider(params: CompletionParameters): YamlMetaTypeProvider {
        return DeftMetaTypeProvider.getInstance()
    }
}
