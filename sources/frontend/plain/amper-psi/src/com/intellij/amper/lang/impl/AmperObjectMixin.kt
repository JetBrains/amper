/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package com.intellij.amper.lang.impl

import com.intellij.amper.lang.AmperContextBlock
import com.intellij.amper.lang.AmperContextualStatement
import com.intellij.amper.lang.AmperObject
import com.intellij.amper.lang.AmperObjectElement
import com.intellij.amper.lang.AmperProperty
import com.intellij.amper.lang.AmperValue
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

val AmperObject.propertyList get() = this.objectElementList.propertyList()
val AmperObject.collectionItems get() = this.objectElementList.collectionItems()
val AmperObject.allObjectElements get() = this.objectElementList.allElementsRecursively()

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
      is AmperContextualStatement -> listOfNotNull(it.objectElement).collectionItems()
      is AmperContextBlock -> it.objectElementList.collectionItems()
      else -> emptyList()
    }
  }
}
