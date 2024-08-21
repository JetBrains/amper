/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.frontend.schema.AndroidSettings
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.notExists
import kotlin.reflect.KProperty0
import kotlin.io.path.div
import kotlin.io.path.reader

object SigningConfigurationIncorrect : AomSingleModuleDiagnosticFactory {
    override val diagnosticId: BuildProblemId = "signing.configuration.incorrect"

    context(ProblemReporterContext)
    override fun PotatoModule.analyze() {
        this.source.moduleDir?.let { moduleDir ->
            fragments.filter { !it.isTest }.forEach { fragment ->
                val android = fragment.settings.android
                val signing = android.signing
                if (signing.enabled) {
                    val propertiesFile = moduleDir / signing.propertiesFile
                    if (propertiesFile.notExists()) {
                        problemReporter.reportMessage(
                            SigningEnabledWithoutPropertiesFile(
                                android.targetProperty,
                                propertiesFile
                            )
                        )
                    } else {
                        val properties = Properties()
                        propertiesFile.reader().use { properties.load(it) }
                        val keys = setOf("storeFile", "storePassword", "keyAlias", "keyPassword")
                        for (key in keys) {
                            if (!properties.containsKey(key)) {
                                problemReporter.reportMessage(
                                    KeystorePropertiesDoesNotContainKey(
                                        android.targetProperty,
                                        key,
                                        propertiesFile
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private val AndroidSettings.targetProperty: KProperty0<*>
        get() {
            val psiElement = signing.extractPsiElement()
            val shortForm = if (psiElement.children.size == 1) {
                psiElement.children.first().text == "enabled"
            } else {
                false
            }
            val targetProperty: KProperty0<*> = if (shortForm) {
                this::signing
            } else {
                signing::propertiesFile.extractPsiElementOrNull()?.let {
                    signing::propertiesFile
                } ?: this::signing
            }
            return targetProperty
        }
}

class SigningEnabledWithoutPropertiesFile(
    val targetProperty: KProperty0<*>,
    val propertiesFilePath: Path
) : PsiBuildProblem(Level.Warning) {
    override val element: PsiElement get() = targetProperty.extractPsiElement()
    override val buildProblemId: BuildProblemId = SigningConfigurationIncorrect.diagnosticId
    override val message: String
        get() = SchemaBundle.message(
            messageKey = "keystore.properties.does.not.exist",
            propertiesFilePath
        )
}

class KeystorePropertiesDoesNotContainKey(
    val targetProperty: KProperty0<*>,
    val key: String,
    val propertiesFilePath: Path,
) : PsiBuildProblem(Level.Warning) {
    override val element: PsiElement get() = targetProperty.extractPsiElement()
    override val buildProblemId: BuildProblemId = SigningConfigurationIncorrect.diagnosticId
    override val message: String
        get() = SchemaBundle.message(
            messageKey = "keystore.properties.key.does.not.exist",
            propertiesFilePath,
            key
        )
}
