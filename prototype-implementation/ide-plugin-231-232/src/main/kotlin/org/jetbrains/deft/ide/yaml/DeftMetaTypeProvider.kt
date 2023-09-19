package org.jetbrains.deft.ide.yaml

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import org.jetbrains.yaml.meta.impl.YamlMetaTypeProvider

@Suppress("UnstableApiUsage")
@Service(Service.Level.APP)
internal class DeftMetaTypeProvider : YamlMetaTypeProvider(DeftYamlModelProvider.getInstance().meta, DeftYamlModelProvider.TRACKER) {
    companion object {
        @JvmStatic
        fun getInstance(): DeftMetaTypeProvider = service()
    }
}
