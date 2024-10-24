/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.amper.lang.AmperProperty
import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.PlatformSpecific
import org.jetbrains.amper.frontend.api.ProductTypeSpecific
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.SchemaValuesVisitor
import org.jetbrains.amper.frontend.api.ValueBase
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.yaml.psi.YAMLKeyValue
import kotlin.reflect.full.findAnnotation

object IncorrectSettingsLocation: AomSingleModuleDiagnosticFactory {
    override val diagnosticId: BuildProblemId
        get() = "settings.incorrect.section"

    context(ProblemReporterContext) override fun AmperModule.analyze() {
        val reportedPlaces = mutableSetOf<PsiElement>()
        fragments.forEach { fragment ->
            // relevant setting fragments - those that are defined in the same section
            val allPlatforms = fragments.filter { it.settings.trace == fragment.settings.trace }
                .flatMap { it.platforms.map { it.leaves } }.flatten().toSet()
            object : SchemaValuesVisitor() {
                override fun visitValue(valueBase: ValueBase<*>) {
                    valueBase.property.findAnnotation<PlatformSpecific>()?.let {
                        if (it.platforms.flatMap { it.leaves }.intersect(allPlatforms).isEmpty()
                            && valueBase.withoutDefault != null
                            && valueBase.trace is PsiTrace
                            && reportedPlaces.add((valueBase.trace as PsiTrace).psiElement)) {
                            problemReporter.reportMessage(
                                IncorrectSettingsSection(valueBase,
                                    SchemaBundle.message("settings.incorrect.platform",
                                        allPlatforms.joinToString { it.pretty },
                                        it.platforms.joinToString { it.pretty }))
                            )
                        }
                    }
                    valueBase.property.findAnnotation<ProductTypeSpecific>()?.let {
                        if (!it.productTypes.contains(fragment.module.type)
                            && valueBase.withoutDefault != null
                            && valueBase.trace is PsiTrace
                            && reportedPlaces.add((valueBase.trace as PsiTrace).psiElement)) {
                            problemReporter.reportMessage(
                                IncorrectSettingsSection(valueBase, SchemaBundle.message("settings.incorrect.product.type",
                                    fragment.module.type.value,
                                    it.productTypes.joinToString { it.value }))
                            )
                        }
                    }
                    super.visitValue(valueBase)
                }
            }.visit(fragment.settings)
        }
    }
}

class IncorrectSettingsSection(
    @UsedInIdePlugin
    val versionProp: ValueBase<*>,
    private val customMessage: String
) : PsiBuildProblem(Level.Warning) {
    override val element: PsiElement
        get() = versionProp.extractPsiElement().let { element ->
            // highlight only property keys
            when (element) {
                is YAMLKeyValue -> element.key ?: element
                is AmperProperty -> element.nameElement ?: element
                else -> element
            }
        }

    override val buildProblemId: BuildProblemId =
        IncorrectSettingsLocation.diagnosticId

    override val message: String
        get() = customMessage
}