/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.core.properties.readProperties
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.frontend.schema.AndroidSettings
import org.jetbrains.amper.frontend.schema.KeystoreProperty
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.schema.storeFile
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.notExists
import kotlin.reflect.KProperty0

abstract class SigningConfigurationIncorrect : AomSingleModuleDiagnosticFactory {
    context(ProblemReporterContext)
    override fun AmperModule.analyze() {
        if (type == ProductType.ANDROID_APP) {
            source.moduleDir?.let { moduleDir ->
                fragments.filter { !it.isTest }.filter { it.platforms == setOf(Platform.ANDROID) }.forEach { fragment ->
                    val android = fragment.settings.android
                    val signing = android.signing
                    if (signing.enabled) {
                        analyze(moduleDir, android)
                    }
                }
            }
        }
    }

    protected abstract fun ProblemReporterContext.analyze(moduleDir: Path, android: AndroidSettings)

    protected val AndroidSettings.targetProperty: KProperty0<*>
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

object SigningEnabledWithoutPropertiesFileFactory : SigningConfigurationIncorrect() {
    override fun ProblemReporterContext.analyze(
        moduleDir: Path, android: AndroidSettings
    ) {
        val signing = android.signing
        val propertiesFile = moduleDir / signing.propertiesFile
        if (propertiesFile.notExists()) {
            problemReporter.reportMessage(
                SigningEnabledWithoutPropertiesFile(
                    android.targetProperty, propertiesFile
                )
            )
        }
    }

    override val diagnosticId: BuildProblemId = "keystore.properties.does.not.exist"
}

object KeystorePropertiesDoesNotContainKeyFactory : SigningConfigurationIncorrect() {
    override fun ProblemReporterContext.analyze(
        moduleDir: Path, android: AndroidSettings
    ) {
        val signing = android.signing
        val propertiesFile = moduleDir / signing.propertiesFile
        if (propertiesFile.exists()) {
            val properties = propertiesFile.readProperties()
            for (property in KeystoreProperty.entries.toTypedArray()) {
                if (!properties.containsKey(property.key)) {
                    problemReporter.reportMessage(
                        KeystorePropertiesDoesNotContainKey(
                            android.targetProperty, property.key, propertiesFile
                        )
                    )
                }
            }
        }
    }

    override val diagnosticId: BuildProblemId = "keystore.properties.key.does.not.exist"
}

object MandatoryFieldInPropertiesFileMustBePresentFactory : SigningConfigurationIncorrect() {
    override fun ProblemReporterContext.analyze(
        moduleDir: Path, android: AndroidSettings
    ) {
        val signing = android.signing
        val propertiesFile = moduleDir / signing.propertiesFile
        if (propertiesFile.exists()) {
            val properties = propertiesFile.readProperties()
            val mandatoryFields = setOf(KeystoreProperty.StoreFile.key, KeystoreProperty.KeyAlias.key)
            for (key in mandatoryFields) {
                val value = properties.getProperty(key)
                if (value.isNullOrBlank()) {
                    problemReporter.reportMessage(
                        MandatoryFieldInPropertiesFileMustBePresent(
                            android.targetProperty, key, propertiesFile
                        )
                    )
                }
            }
        }
    }

    override val diagnosticId: BuildProblemId = "keystore.properties.value.required"
}

object KeystoreMustExistFactory : SigningConfigurationIncorrect() {
    override fun ProblemReporterContext.analyze(moduleDir: Path, android: AndroidSettings) {
        val signing = android.signing
        val propertiesFile = moduleDir / signing.propertiesFile
        if (propertiesFile.exists()) {
            val properties = propertiesFile.readProperties()
            val storeFilePath = properties.storeFile ?: return
            val storeFile = (moduleDir / storeFilePath).normalize().toAbsolutePath()
            if (storeFile.notExists()) {
                problemReporter.reportMessage(
                    KeystoreFileDoesNotExist(
                        android.targetProperty, Path(storeFilePath)
                    )
                )
            }
        }
    }

    override val diagnosticId: BuildProblemId = "keystore.properties.file.does.not.exist"
}

class SigningEnabledWithoutPropertiesFile(
    val targetProperty: KProperty0<*>, val propertiesFilePath: Path
) : PsiBuildProblem(Level.Warning) {
    override val element: PsiElement get() = targetProperty.extractPsiElement()
    override val buildProblemId: BuildProblemId = SigningEnabledWithoutPropertiesFileFactory.diagnosticId
    override val message: String
        get() = SchemaBundle.message(
            messageKey = buildProblemId, propertiesFilePath
        )
}

class KeystorePropertiesDoesNotContainKey(
    val targetProperty: KProperty0<*>,
    val key: String,
    val propertiesFilePath: Path,
) : PsiBuildProblem(Level.Warning) {
    override val element: PsiElement get() = targetProperty.extractPsiElement()
    override val buildProblemId: BuildProblemId = KeystorePropertiesDoesNotContainKeyFactory.diagnosticId
    override val message: String
        get() = SchemaBundle.message(
            messageKey = buildProblemId, propertiesFilePath, key
        )
}

class MandatoryFieldInPropertiesFileMustBePresent(
    val targetProperty: KProperty0<*>, val key: String, val propertiesFilePath: Path
) : PsiBuildProblem(Level.Warning) {
    override val element: PsiElement get() = targetProperty.extractPsiElement()
    override val buildProblemId: BuildProblemId = MandatoryFieldInPropertiesFileMustBePresentFactory.diagnosticId
    override val message: String
        get() = SchemaBundle.message(
            messageKey = buildProblemId, propertiesFilePath, key
        )
}

class KeystoreFileDoesNotExist(
    val targetProperty: KProperty0<*>, val keystorePath: Path
) : PsiBuildProblem(Level.Warning) {
    override val element: PsiElement get() = targetProperty.extractPsiElement()
    override val buildProblemId: BuildProblemId = KeystoreMustExistFactory.diagnosticId
    override val message: String
        get() = SchemaBundle.message(
            messageKey = buildProblemId, keystorePath
        )
}
