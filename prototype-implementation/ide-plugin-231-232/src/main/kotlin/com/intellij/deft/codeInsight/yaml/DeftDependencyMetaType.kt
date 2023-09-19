@file:Suppress("UnstableApiUsage")

package com.intellij.deft.codeInsight.yaml

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.deft.codeInsight.DeftPotReference
import com.intellij.deft.codeInsight.DeftProduct
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.yaml.meta.model.CompletionContext
import org.jetbrains.yaml.meta.model.YamlReferenceType
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence

internal class DeftDependencyMetaType(private val product: DeftProduct?, private val isTest: Boolean)
  : YamlReferenceType("deft-dependency") {

  companion object {

    private val commonDependencies = listOf(
      "org.jetbrains.kotlinx:kotlinx-datetime:0.4.0",
    )

    private val commonTestDependencies = listOf(
      "org.jetbrains.kotlin:kotlin-test:1.8.20",
      "org.jetbrains.kotlin:kotlin-test:1.9.0",
      "junit:junit:4.13.2",
      "org.junit.jupiter:junit-jupiter-api:5.9.3",
      "org.junit.jupiter:junit-jupiter-engine:5.9.3",
      "org.junit.vintage:junit-vintage-engine:5.9.3",
    )

    private val composeDependencies = listOf(
      "org.jetbrains.compose.animation:animation:1.4.1",
      "org.jetbrains.compose.animation:animation-graphics:1.4.1",
      "org.jetbrains.compose.foundation:foundation:1.4.1",
      "org.jetbrains.compose.material:material:1.4.1",
      "org.jetbrains.compose.material3:material3:1.4.1",
      "org.jetbrains.compose.ui:ui:1.4.1",
      "org.jetbrains.compose.runtime:runtime:1.4.1",
      "org.jetbrains.compose.ui:ui-tooling:1.4.1",
      "org.jetbrains.compose.ui:ui-tooling-preview:1.4.1",
    )

    private val composeTestDependencies = listOf(
      "org.jetbrains.compose.ui:ui-test:1.4.1",
      "org.jetbrains.compose.ui:ui-test-junit4:1.4.1",
    )

    private val composeDesktopDependencies = listOf(
      "org.jetbrains.compose.desktop:desktop-jvm:1.4.1",
    )

    // Versions from Compose BOM at https://developer.android.com/jetpack/compose/bom/bom-mapping
    private val androidDependencies = listOf(
      "androidx.core:core-ktx:1.10.1",
      "androidx.appcompat:appcompat:1.6.1",
      "androidx.activity:activity-compose:1.7.2",
      "androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1",
      "androidx.compose.animation:animation:1.4.3",
      "androidx.compose.animation:animation-core:1.4.3",
      "androidx.compose.animation:animation-graphics:1.4.3",
      "androidx.compose.foundation:foundation:1.4.3",
      "androidx.compose.material:material:1.4.3",
      "androidx.compose.material3:material3:1.1.1",
      "androidx.compose.ui:ui:1.4.3",
      "androidx.compose.runtime:runtime:1.4.3",
      "androidx.compose.runtime:runtime-livedata:1.4.3",
      "androidx.compose.runtime:runtime-rxjava2:1.4.3",
      "androidx.compose.runtime:runtime-rxjava3:1.4.3",
      "androidx.compose.runtime:runtime-saveable:1.4.3",
      "androidx.compose.ui:ui-geometry:1.4.3",
      "androidx.compose.ui:ui-graphics:1.4.3",
      "androidx.compose.ui:ui-text:1.4.3",
      "androidx.compose.ui:ui-tooling:1.4.3",
      "androidx.compose.ui:ui-tooling-data:1.4.3",
      "androidx.compose.ui:ui-tooling-preview:1.4.3",
      "androidx.compose.ui:ui-unit:1.4.3",
      "androidx.compose.ui:ui-util:1.4.3",
      "androidx.compose.ui:ui-viewbinding:1.4.3",
    )

    // Versions from Compose BOM at https://developer.android.com/jetpack/compose/bom/bom-mapping
    private val androidTestDependencies = listOf(
      "androidx.compose.ui:ui-test:1.4.3",
      "androidx.compose.ui:ui-test-junit4:1.4.3",
      "androidx.compose.ui:ui-test-manifest:1.4.3",
    )

    private val ktorDependencies = listOf(
      "io.ktor:ktor-server-auth:2.3.2",
      "io.ktor:ktor-server-core:2.3.2",
      "io.ktor:ktor-server-content-negotiation:2.3.2",
      "io.ktor:ktor-server-html-builder:2.3.2",
      "io.ktor:ktor-server-locations:2.3.2",
      "io.ktor:ktor-server-netty:2.3.2",
      "io.ktor:ktor-server-resources:2.3.2",
      "io.ktor:ktor-server-sessions:2.3.2",
      "io.ktor:ktor-server-websockets:2.3.2",
      "io.ktor:ktor-client-auth:2.3.2",
      "io.ktor:ktor-client-cio:2.3.2",
      "io.ktor:ktor-client-content-negotiation:2.3.2",
      "io.ktor:ktor-client-core:2.3.2",
      "io.ktor:ktor-client-encoding:2.3.2",
      "io.ktor:ktor-client-logging:2.3.2",
      "io.ktor:ktor-client-resources:2.3.2",
      "io.ktor:ktor-client-websockets:2.3.2",
    )

    private val ktorTestDependencies = listOf(
      "io.ktor:ktor-server-test-host:2.3.2",
      "io.ktor:ktor-client-mock:2.3.2",
    )
  }

  override fun getReferencesFromValue(valueScalar: YAMLScalar): Array<PsiReference> {
    return arrayOf(DeftPotReference(valueScalar, false))
  }

  override fun getValueLookups(insertedScalar: YAMLScalar, completionContext: CompletionContext?): List<LookupElement> {
    val parent = PsiTreeUtil.getParentOfType(insertedScalar, YAMLKeyValue::class.java)
      ?.takeIf { it.keyText.matches("(test-)?dependencies(@.+)?".toRegex()) }
    val existingDependencies = parent?.let { it.value as? YAMLSequence }
                                 ?.items
                                 ?.mapNotNull { it.value as? YAMLScalar }
                                 ?.map { it.textValue.substringBeforeLast(':') }
                                 ?.toSet() ?: emptySet()
    val platforms = parent?.keyText?.split('@')?.getOrNull(1)?.split('+') ?: product?.platforms ?: listOf()
    return (commonDependencies() + composeDependencies() + ktorDependencies() +
            platforms.asSequence().mapNotNull {
              when (it) {
                "android" -> androidDependencies()
                "linux", "macos" -> composeDesktopDependencies.asSequence()
                else -> null
              }
            }.flatten())
      .toLookupList(existingDependencies)
  }

  private fun commonDependencies(): Sequence<String> = commonDependencies.asSequence() +
                                                       if (isTest) commonTestDependencies.asSequence() else emptySequence()

  private fun composeDependencies(): Sequence<String> = composeDependencies.asSequence() +
                                                        if (isTest) composeTestDependencies.asSequence() else emptySequence()

  private fun ktorDependencies(): Sequence<String> = ktorDependencies.asSequence() +
                                                     if (isTest) ktorTestDependencies.asSequence() else emptySequence()

  private fun androidDependencies(): Sequence<String> = androidDependencies.asSequence() +
                                                        (if (isTest) androidTestDependencies.asSequence() else emptySequence())

  private fun Sequence<String>.toLookupList(existingDependencies: Set<String>) =
    filterNot { existingDependencies.contains(it.substringBeforeLast(':')) }
      .map { LookupElementBuilder.create(it) }
      .toList()
}