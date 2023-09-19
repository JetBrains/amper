package com.intellij.deft.codeInsight.yaml

import com.intellij.deft.codeInsight.getProduct
import com.intellij.deft.codeInsight.isPotTemplate
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.yaml.meta.model.Field
import org.jetbrains.yaml.meta.model.ModelAccess

@Suppress("UnstableApiUsage")
@Service(Service.Level.APP)
internal class DeftYamlModelProvider private constructor() {
  val meta: ModelAccess = ModelAccess { document ->
    val product = document.getProduct()
    if (document.containingFile.originalFile.isPotTemplate()) {
      Field("<deft-template-root>", DeftYamlModel(product, isTemplate = true))
    }
    else {
      Field("<deft-root>", DeftYamlModel(product, isTemplate = false))
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): DeftYamlModelProvider = service()

    @JvmField
    internal val TRACKER = ModificationTracker.NEVER_CHANGED
  }
}
