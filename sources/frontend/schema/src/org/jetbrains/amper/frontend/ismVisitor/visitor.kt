/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.ismVisitor

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.api.withoutDefault
import org.jetbrains.amper.frontend.schema.AmperLayout
import org.jetbrains.amper.frontend.schema.AndroidSettings
import org.jetbrains.amper.frontend.schema.Base
import org.jetbrains.amper.frontend.schema.ComposeSettings
import org.jetbrains.amper.frontend.schema.Dependency
import org.jetbrains.amper.frontend.schema.JavaSettings
import org.jetbrains.amper.frontend.schema.JvmSettings
import org.jetbrains.amper.frontend.schema.KotlinSettings
import org.jetbrains.amper.frontend.schema.Meta
import org.jetbrains.amper.frontend.schema.Modifiers
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.ModuleProduct
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.schema.Repository
import org.jetbrains.amper.frontend.schema.SerializationSettings
import org.jetbrains.amper.frontend.schema.Settings
import java.nio.file.Path

enum class Phase {
  START,END
}

interface IsmVisitor {
//  fun visitBase(base: Base)
  fun visitModule(module: Module)
  fun visitProduct(product: ModuleProduct)
  fun visitProductType(productType: ProductType)
  fun visitProductPlatform(productPlatform: Platform)
  fun visitModuleMeta(meta: Meta)
  fun visitModuleMetaLayout(layout: AmperLayout)
  fun visitAlias(name: String, platforms: Set<Platform>)
  fun visitApply(path: Path)
  fun visitRepositories(repo: List<Repository>)
  fun visitRepository(repo: Repository)
  fun visitCredentials(credentials: Repository.Credentials)
  fun visitDependencies(dependencies: Map<Modifiers, List<Dependency>>)
  fun visitDependencies(modifiers: Modifiers, dependencies: List<Dependency>)
  fun visitDependency(dependency: Dependency)
  fun visitTestDependencies(dependencies: Map<Modifiers, List<Dependency>>)
  fun visitTestDependencies(modifiers: Modifiers, dependencies: List<Dependency>)
  fun visitSettings(settings: Map<Modifiers, Settings>)
  fun visitSettings(modifiers: Modifiers, settings: Settings)
  fun visitTestSettings(settings: Map<Modifiers, Settings>)
  fun visitTestSettings(modifiers: Modifiers, settings: Settings)
  fun visitSettings(settings: Settings)
  fun visitJavaSettings(settings: JavaSettings)
  fun visitJvmSettings(settings: JvmSettings)
  fun visitAndroidSettings(settings: AndroidSettings)
  fun visitKotlinSettings(settings: KotlinSettings)
  fun visitComposeSettings(settings: ComposeSettings)
  fun visitSerializationSettings(settings: SerializationSettings)
}

fun Module.accept(visitor: IsmVisitor) {
  visitor.visitModule(this)
  (this as Base).accept(visitor)
  ::product.withoutDefault?.accept(visitor)
  ::module.withoutDefault?.accept(visitor)
  ::aliases.withoutDefault?.forEach { visitor.visitAlias(it.key, it.value) }
  ::apply.withoutDefault?.forEach { visitor.visitApply(it) }
}

fun Module.visit(ismVisitor: IsmVisitor) {
  ismVisitor.visitProduct(product)
  ismVisitor.visitModuleMeta(module)
  aliases?.forEach {
    ismVisitor.visitAlias(it.key, it.value)
  }
  apply?.forEach {
    ismVisitor.visitApply(it)
  }
  repositories?.forEach {
    ismVisitor.visitRepository(it)
  }
  dependencies?.forEach {
    ismVisitor.visitDependencies(it.key, it.value)
  }
  settings.forEach {
    ismVisitor.visitSettings(it.key, it.value)
  }
  `test-dependencies`?.forEach {
    ismVisitor.visitTestDependencies(it.key, it.value)
  }
  `test-settings`.forEach {
    ismVisitor.visitSettings(it.key, it.value)
  }
}

fun ModuleProduct.visit(ismVisitor: IsmVisitor) {
  ismVisitor.visitProductType(type)
  platforms.forEach {
    ismVisitor.visitProductPlatform(it)
  }
}

fun Dependency.accept(visitor: IsmVisitor) {
  visitor.visitDependency(this)
}

fun ModuleProduct.accept(visitor: IsmVisitor) {
  visitor.visitProduct(this)
  ::type.withoutDefault?.let { visitor.visitProductType(it) }
  ::platforms.withoutDefault?.forEach {
    visitor.visitProductPlatform(it)
  }
}

fun Meta.accept(visitor: IsmVisitor) {
  visitor.visitModuleMeta(this)
  ::layout.withoutDefault?.let { visitor.visitModuleMetaLayout(it) }
}

fun Base.accept(visitor: IsmVisitor) {
  ::repositories.withoutDefault?.let { repositories ->
    visitor.visitRepositories(repositories)
    repositories.forEach { it.accept(visitor) }
  }
  ::dependencies.withoutDefault?.visitDependencies(visitor)
  ::settings.withoutDefault?.visitSettings(visitor)
  ::`test-dependencies`.withoutDefault?.visitTestDependencies(visitor)
  ::`test-settings`.withoutDefault?.visitTestSettings(visitor)
}

private fun Map<Modifiers, List<Dependency>>.visitDependencies(visitor: IsmVisitor) {
  visitor.visitDependencies(this)
  this.forEach {
    visitor.visitDependencies(it.key, it.value)
    it.value.forEach { dependency ->
      dependency.accept(visitor)
    }
  }
}

private fun Map<Modifiers, List<Dependency>>.visitTestDependencies(visitor: IsmVisitor) {
  visitor.visitTestDependencies(this)
  this.forEach {
    visitor.visitTestDependencies(it.key, it.value)
    it.value.forEach { dependency ->
      dependency.accept(visitor)
    }
  }
}

private fun Map<Modifiers, Settings>.visitSettings(visitor: IsmVisitor) {
  visitor.visitSettings(this)
  this.forEach {
    visitor.visitSettings(it.key, it.value)
    it.value.accept(visitor)
  }
}

private fun Map<Modifiers, Settings>.visitTestSettings(visitor: IsmVisitor) {
  visitor.visitTestSettings(this)
  this.forEach {
    visitor.visitTestSettings(it.key, it.value)
    it.value.accept(visitor)
  }
}

fun Settings.accept(visitor: IsmVisitor) {
  visitor.visitSettings(this)
  ::java.withoutDefault?.accept(visitor)
  ::jvm.withoutDefault?.accept(visitor)
  ::android.withoutDefault?.accept(visitor)
  ::kotlin.withoutDefault?.accept(visitor)
  ::compose.withoutDefault?.accept(visitor)
//  java.withoutDefault?.accept(visitor)
}

fun JavaSettings.accept(visitor: IsmVisitor) {
  visitor.visitJavaSettings(this)
}

fun JvmSettings.accept(visitor: IsmVisitor) {
  visitor.visitJvmSettings(this)
}

fun AndroidSettings.accept(visitor: IsmVisitor) {
  visitor.visitAndroidSettings(this)
}

fun KotlinSettings.accept(visitor: IsmVisitor) {
  visitor.visitKotlinSettings(this)
}

fun ComposeSettings.accept(visitor: IsmVisitor) {
  visitor.visitComposeSettings(this)
}

fun SerializationSettings.accept(visitor: IsmVisitor) {
  visitor.visitSerializationSettings(this)
}

fun Repository.accept(visitor: IsmVisitor) {
  visitor.visitRepository(this)
  ::credentials.withoutDefault?.accept(visitor)
}

fun Repository.Credentials.accept(visitor: IsmVisitor) {
  visitor.visitCredentials(this)
}
