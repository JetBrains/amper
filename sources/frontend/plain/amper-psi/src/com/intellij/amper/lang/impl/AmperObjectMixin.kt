package com.intellij.amper.lang.impl

import com.intellij.amper.lang.*
import com.intellij.lang.ASTNode
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

abstract class AmperObjectMixin(node: ASTNode): AmperObject, AmperElementImpl(node) {
  private val myPropertyCache: CachedValueProvider<Map<String, AmperProperty>> = CachedValueProvider<Map<String, AmperProperty>> {
    val cache: MutableMap<String, AmperProperty> = HashMap()
    for (property in propertyList) {
      val propertyName: String = property.name ?: continue
      // Preserve the old behavior - return the first value in findProperty()
      if (!cache.containsKey(propertyName)) {
        cache[propertyName] = property
      }
    }
    CachedValueProvider.Result.createSingleDependency(cache, this)
  }

  override fun findProperty(name: String): AmperProperty? {
    return CachedValuesManager.getCachedValue(this, myPropertyCache)[name]
  }
}

val AmperObject.propertyList: List<AmperProperty> get() = this.objectElementList.propertyList()
val AmperObject.collectionItems: List<AmperValue> get() = this.objectElementList.collectionItems()
val AmperObject.allObjectElements: List<AmperObjectElement> get() = this.objectElementList.allElementsRecursively()

private fun List<AmperObjectElement>.allElementsRecursively(): List<AmperObjectElement> {
  return flatMap {
    when (it) {
      is AmperContextualStatement -> listOfNotNull(it.objectElement).allElementsRecursively()
      is AmperContextBlock -> it.objectElementList.allElementsRecursively()
      else -> listOf(it)
    }
  }
}

private fun List<AmperObjectElement>.propertyList(): List<AmperProperty> {
  return this.flatMap {
    when (it) {
      is AmperProperty -> listOfNotNull(it)
      is AmperContextualStatement -> listOfNotNull(it.objectElement).propertyList()
      is AmperContextBlock -> it.objectElementList.propertyList()
      else -> emptyList()
    }
  }
}

private fun List<AmperObjectElement>.collectionItems(): List<AmperValue> {
  return this.flatMap {
    when (it) {
      is AmperProperty -> listOfNotNull(it.takeIf { it.value == null }?.nameElement)
      is AmperInvocationElement -> listOfNotNull(it.invocationExpression)
      is AmperContextualStatement -> listOfNotNull(it.objectElement).collectionItems()
      is AmperContextBlock -> it.objectElementList.collectionItems()
      else -> emptyList()
    }
  }
}
