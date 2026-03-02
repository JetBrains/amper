/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.SchemaValueDelegate
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.frontend.schema.AndroidSettings
import org.jetbrains.amper.frontend.schema.KeystoreProperty
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.schema.storeFile
import org.jetbrains.amper.frontend.types.generated.*
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.DiagnosticId
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.amper.stdlib.properties.readProperties
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.notExists

abstract class SigningConfigurationIncorrect : AomSingleModuleDiagnosticFactory {

    override fun analyze(module: AmperModule, problemReporter: ProblemReporter) {
        if (module.type == ProductType.ANDROID_APP) {
            module.fragments.filter { !it.isTest }.filter { it.platforms == setOf(Platform.ANDROID) }.forEach { fragment ->
                val android = fragment.settings.android
                val signing = android.signing
                if (signing.enabled) {
                    analyze(module.source.moduleDir, android, problemReporter)
                }
            }
        }
    }

    /**
     * Analyzes the given [android] settings of the module at [moduleDir] and reports any problems using the given
     * [problemReporter].
     */
    protected abstract fun analyze(moduleDir: Path, android: AndroidSettings, problemReporter: ProblemReporter)

    protected val AndroidSettings.targetProperty: SchemaValueDelegate<*>
        get() {
            val psiElement = signing.extractPsiElement()
            val shortForm = if (psiElement.children.size == 1) {
                psiElement.children.first().text == "enabled"
            } else {
                false
            }
            val targetProperty: SchemaValueDelegate<*> = if (shortForm) {
                signingDelegate
            } else {
                signing.propertiesFileDelegate.extractPsiElementOrNull()?.let {
                    signing.propertiesFileDelegate
                } ?: signingDelegate
            }
            return targetProperty
        }
}

object SigningEnabledWithoutPropertiesFileFactory : SigningConfigurationIncorrect() {
    @Deprecated(
        message = "Use SigningEnabledWithoutPropertiesFile.ID",
        replaceWith = ReplaceWith("SigningEnabledWithoutPropertiesFile.ID"),
    )
    val diagnosticId: BuildProblemId = SigningEnabledWithoutPropertiesFile.ID

    override fun analyze(moduleDir: Path, android: AndroidSettings, problemReporter: ProblemReporter) {
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

}

object KeystorePropertiesDoesNotContainKeyFactory : SigningConfigurationIncorrect() {
    @Deprecated(
        message = "Use KeystorePropertiesDoesNotContainKey.ID",
        replaceWith = ReplaceWith("KeystorePropertiesDoesNotContainKey.ID"),
    )
    val diagnosticId: BuildProblemId = KeystorePropertiesDoesNotContainKey.ID

    override fun analyze(moduleDir: Path, android: AndroidSettings, problemReporter: ProblemReporter) {
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

}

object MandatoryFieldInPropertiesFileMustBePresentFactory : SigningConfigurationIncorrect() {
    @Deprecated(
        message = "Use MandatoryFieldInPropertiesFileMustBePresent.ID",
        replaceWith = ReplaceWith("MandatoryFieldInPropertiesFileMustBePresent.ID"),
    )
    val diagnosticId: BuildProblemId = MandatoryFieldInPropertiesFileMustBePresent.ID

    override fun analyze(moduleDir: Path, android: AndroidSettings, problemReporter: ProblemReporter) {
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

}

object KeystoreMustExistFactory : SigningConfigurationIncorrect() {
    @Deprecated(
        message = "Use KeystoreFileDoesNotExist.ID",
        replaceWith = ReplaceWith("KeystoreFileDoesNotExist.ID"),
    )
    val diagnosticId: BuildProblemId = KeystoreFileDoesNotExist.ID

    override fun analyze(moduleDir: Path, android: AndroidSettings, problemReporter: ProblemReporter) {
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

}

class SigningEnabledWithoutPropertiesFile(
    val targetProperty: SchemaValueDelegate<*>, val propertiesFilePath: Path
) : PsiBuildProblem(Level.Warning, BuildProblemType.InconsistentConfiguration) {
    companion object {
        const val ID = "keystore.properties.does.not.exist"
    }

    override val element: PsiElement get() = targetProperty.extractPsiElement()
    @Deprecated("Should be replaced with `diagnosticId` property", replaceWith = ReplaceWith("diagnosticId"))
    override val buildProblemId = ID
    override val diagnosticId: DiagnosticId = FrontendDiagnosticId.SigningEnabledWithoutPropertiesFile
    override val message: @Nls String
        get() = SchemaBundle.message(
            messageKey = "keystore.properties.does.not.exist", propertiesFilePath
        )
}

class KeystorePropertiesDoesNotContainKey(
    val targetProperty: SchemaValueDelegate<*>,
    val key: String,
    val propertiesFilePath: Path,
) : PsiBuildProblem(Level.Warning, BuildProblemType.Generic) {
    companion object {
        const val ID = "keystore.properties.key.does.not.exist"
    }

    override val element: PsiElement get() = targetProperty.extractPsiElement()
    @Deprecated("Should be replaced with `problemId` property", replaceWith = ReplaceWith("problemId"))
    override val buildProblemId = ID
    override val diagnosticId: DiagnosticId = FrontendDiagnosticId.KeystorePropertiesDoesNotContainKey

    override val message: @Nls String
        get() = SchemaBundle.message(
            messageKey = "keystore.properties.key.does.not.exist", propertiesFilePath, key
        )
}

class MandatoryFieldInPropertiesFileMustBePresent(
    val targetProperty: SchemaValueDelegate<*>, val key: String, val propertiesFilePath: Path
) : PsiBuildProblem(Level.Warning, BuildProblemType.Generic) {
    companion object {
        const val ID = "keystore.properties.value.required"
    }

    override val element: PsiElement get() = targetProperty.extractPsiElement()
    @Deprecated("Should be replaced with `diagnosticId` property", replaceWith = ReplaceWith("diagnosticId"))
    override val buildProblemId = ID
    override val diagnosticId: DiagnosticId = FrontendDiagnosticId.MandatoryFieldInPropertiesFileMustBePresent
    override val message: @Nls String
        get() = SchemaBundle.message(
            messageKey = "keystore.properties.value.required", propertiesFilePath, key
        )
}

class KeystoreFileDoesNotExist(
    val targetProperty: SchemaValueDelegate<*>, val keystorePath: Path
) : PsiBuildProblem(Level.Warning, BuildProblemType.UnresolvedReference) {
    companion object {
        const val ID = "keystore.properties.file.does.not.exist"
    }

    override val element: PsiElement get() = targetProperty.extractPsiElement()
    @Deprecated("Should be replaced with `problemId` property", replaceWith = ReplaceWith("problemId"))
    override val buildProblemId = ID
    override val diagnosticId: DiagnosticId = FrontendDiagnosticId.KeystoreFileDoesNotExist
    override val message: @Nls String
        get() = SchemaBundle.message(
            messageKey = "keystore.properties.file.does.not.exist", keystorePath
        )
}
