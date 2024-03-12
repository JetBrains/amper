package com.intellij.amper.lang.impl

import com.intellij.amper.lang.AmperObject
import com.intellij.amper.lang.AmperProperty
import com.intellij.amper.lang.AmperValue
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
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

val AmperObject.propertyList get() = this.objectElementList.filterIsInstance<AmperProperty>()
val AmperObject.collectionItems get() = this.objectElementList.filterIsInstance<AmperValue>()