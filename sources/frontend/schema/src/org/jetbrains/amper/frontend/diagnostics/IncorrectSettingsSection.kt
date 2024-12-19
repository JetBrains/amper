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
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.ContextAgnostic
import org.jetbrains.amper.frontend.api.PathAwareSchemaValuesVisitor
import org.jetbrains.amper.frontend.api.PlatformSpecific
import org.jetbrains.amper.frontend.api.ProductTypeSpecific
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.api.ValueBase
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.yaml.psi.YAMLKeyValue
import kotlin.reflect.full.findAnnotation

object IncorrectSettingsLocation : AomSingleModuleDiagnosticFactory {
    override val diagnosticId: BuildProblemId
        get() = "settings.incorrect.section"

    context(ProblemReporterContext)
    override fun AmperModule.analyze() {
        val reportedPlaces = mutableSetOf<PsiElement>()
        fragments.forEach { fragment ->
            // relevant setting fragments - those that are defined in the same section
            val allPlatforms = fragments.filter { it.settings.trace == fragment.settings.trace }
                .flatMap { it.platforms.flatMap { it.leaves } }.toSet()
            object : PathAwareSchemaValuesVisitor() {
                override fun visitValue(it: ValueBase<*>) {
                    if (rootFragment !== fragment && fragment.settings != rootFragment.settings)
                        it.checkIncorrectNoModifierOnly(reportedPlaces)
                    it.checkIncorrectPlatformSpecific(allPlatforms, reportedPlaces)
                    it.checkIncorrectProductSpecific(fragment, reportedPlaces)
                    super.visitValue(it)
                }
            }.visit(fragment.settings)
        }
    }

    /**
     * Check that settings marked with [ContextAgnostic] should be located only
     * in root fragment.
     */
    context(ProblemReporterContext, PathAwareSchemaValuesVisitor)
    private fun ValueBase<*>.checkIncorrectNoModifierOnly(reportedPlaces: MutableSet<PsiElement>) =
        property.findAnnotation<ContextAgnostic>()?.let {
            if (isSetExplicitly && tryAddReportedPlace(reportedPlaces))
                problemReporter.reportMessage(
                    IncorrectSettingsSection(
                        this,
                        // Currently we have only platforms or aliases modifiers.
                        messageKey = "settings.unexpected.context",
                        level = Level.Error,
                    )
                )
        }

    context(ProblemReporterContext, PathAwareSchemaValuesVisitor)
    private fun ValueBase<*>.checkIncorrectPlatformSpecific(
        allPlatforms: Set<Platform>,
        reportedPlaces: MutableSet<PsiElement>,
    ) = property.findAnnotation<PlatformSpecific>()?.let {
        if (it.platforms.flatMap { it.leaves }.intersect(allPlatforms).isEmpty()
            && isSetExplicitly
            && tryAddReportedPlace(reportedPlaces)
        ) problemReporter.reportMessage(
            IncorrectSettingsSection(
                this,
                "settings.incorrect.platform",
                allPlatforms.joinToString { it.pretty },
                it.platforms.joinToString { it.pretty },
            )
        )
    }

    context(ProblemReporterContext, PathAwareSchemaValuesVisitor)
    private fun ValueBase<*>.checkIncorrectProductSpecific(
        fragment: Fragment,
        reportedPlaces: MutableSet<PsiElement>,
    ) = property.findAnnotation<ProductTypeSpecific>()?.let {
        if (!it.productTypes.contains(fragment.module.type)
            && isSetExplicitly
            && tryAddReportedPlace(reportedPlaces)
        ) problemReporter.reportMessage(
            IncorrectSettingsSection(
                this,
                "settings.incorrect.product.type",
                fragment.module.type.value,
                it.productTypes.joinToString { it.value },
            )
        )
    }

    context(PathAwareSchemaValuesVisitor)
    private val ValueBase<*>.isSetExplicitly: Boolean
        get() {
            // Check that we are not inheriting value.
            val noInheritance = currentPath.none { it.state == ValueBase.ValueState.INHERITED }
            return when (state) {
                ValueBase.ValueState.EXPLICIT -> currentPath.isEmpty() || noInheritance
                ValueBase.ValueState.MERGED -> currentPath.isEmpty() || noInheritance
                else -> false
            }
        }

    private fun Traceable.tryAddReportedPlace(reportedPlaces: MutableSet<PsiElement>) =
        extractPsiElementOrNull()?.let { reportedPlaces.add(it) } == true
}

class IncorrectSettingsSection private constructor(
    @UsedInIdePlugin
    val versionProp: ValueBase<*>,
    private val customMessage: String,
    level: Level = Level.Warning,
) : PsiBuildProblem(level) {

    constructor(versionProp: ValueBase<*>, messageKey: String, vararg values: Any?, level: Level = Level.Warning) :
            this(versionProp, SchemaBundle.message(messageKey, *values), level)

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