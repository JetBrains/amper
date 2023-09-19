package com.intellij.deft.codeInsight

import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.yaml.psi.*

internal fun PsiElement.getProduct(): DeftProduct? {
  val file = containingFile.originalFile
  if (file !is YAMLFile || !file.isDeftFile()) return null
  return CachedValuesManager.getCachedValue(file) {
    val product = file.findProductElement()
    val result = when (val value = product?.value) {
      is YAMLScalar -> {
        val parts = value.textValue.split('/')
        if (parts.size == 2) DeftProduct(parts[1], listOf(parts[0])) else null
      }
      is YAMLMapping -> {
        val type = value.keyValues.find { it.keyText == "type" }
        val platforms = value.keyValues.find { it.keyText == "platforms" }?.value
        if (type != null && platforms is YAMLSequence) {
          DeftProduct(type.valueText, platforms.items.mapNotNull { it.value as? YAMLScalar }.map { it.textValue })
        }
        else {
          null
        }
      }
      else -> null
    }
    return@getCachedValue CachedValueProvider.Result(result, file)
  }
}

internal fun YAMLFile.findProductElement(): YAMLKeyValue? = documents
  .map { it.topLevelValue }
  .mapNotNull { it as? YAMLMapping }
  .flatMap { it.keyValues }
  .firstOrNull { it.name == "product" }

internal data class DeftProduct(val type: String, val platforms: List<String>)
