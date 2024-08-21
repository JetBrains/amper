/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi.amper

import com.intellij.amper.lang.AmperLiteral
import com.intellij.amper.lang.AmperObject
import com.intellij.amper.lang.AmperProperty
import com.intellij.amper.lang.AmperValue
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.api.applyPsiTrace
import org.jetbrains.amper.frontend.schema.AndroidSettings
import org.jetbrains.amper.frontend.schema.AndroidVersion
import org.jetbrains.amper.frontend.schema.ComposeSettings
import org.jetbrains.amper.frontend.schema.IosFrameworkSettings
import org.jetbrains.amper.frontend.schema.IosSettings
import org.jetbrains.amper.frontend.schema.JUnitVersion
import org.jetbrains.amper.frontend.schema.JavaVersion
import org.jetbrains.amper.frontend.schema.JvmSettings
import org.jetbrains.amper.frontend.schema.KotlinSettings
import org.jetbrains.amper.frontend.schema.KotlinVersion
import org.jetbrains.amper.frontend.schema.KoverHtmlSettings
import org.jetbrains.amper.frontend.schema.KoverSettings
import org.jetbrains.amper.frontend.schema.KoverXmlSettings
import org.jetbrains.amper.frontend.schema.NativeSettings
import org.jetbrains.amper.frontend.schema.PublishingSettings
import org.jetbrains.amper.frontend.schema.SerializationSettings
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.schema.AndroidSigningSettings
import org.jetbrains.amper.frontend.schemaConverter.psi.ConvertCtx

context(ProblemReporterContext, ConvertCtx)
internal fun AmperObject.convertSettings() =
    doConvertSettings().applyPsiTrace(this)

context(ProblemReporterContext, ConvertCtx)
internal fun AmperObject.doConvertSettings() = Settings().apply {
    ::jvm.convertChildValue { (value as? AmperObject)?.convertJvmSettings() }
    ::android.convertChildValue { (value as? AmperObject)?.convertAndroidSettings() }
    ::kotlin.convertChildValue { (value as? AmperObject)?.convertKotlinSettings() }
    ::compose.convertChildValue { convertComposeSettings() }
    ::ios.convertChildValue { (value as? AmperObject)?.convertIosSettings() }
    ::publishing.convertChildValue { (value as? AmperObject)?.convertPublishingSettings() }
    ::kover.convertChildValue { (value as? AmperObject)?.convertKoverSettings() }
    ::native.convertChildValue { (value as? AmperObject)?.convertNativeSettings() }

    ::junit.convertChildEnum(JUnitVersion)
}

context(ProblemReporterContext, ConvertCtx)
internal fun AmperObject.convertJvmSettings() = JvmSettings().apply {
    ::release.convertChildEnum(JavaVersion.Index)
    ::mainClass.convertChildString()
}

context(ProblemReporterContext, ConvertCtx)
internal fun AmperObject.convertAndroidSettings() = AndroidSettings().apply {
    ::compileSdk.convertChildEnum(AndroidVersion)
    ::minSdk.convertChildEnum(AndroidVersion)
    ::maxSdk.convertChildEnum(AndroidVersion)
    ::targetSdk.convertChildEnum(AndroidVersion)
    ::applicationId.convertChildString()
    ::namespace.convertChildString()
    ::signing.convertChildValue { convertAndroidSigningSettings() }
}

context(ProblemReporterContext, ConvertCtx)
internal fun AmperProperty.convertAndroidSigningSettings() = when(value) {
    is AmperLiteral -> (value as? AmperLiteral)?.run { AndroidSigningSettings().apply { ::enabled.convertSelf { (textValue == "enabled") } } }
    else -> (value as AmperObject)?.convertAndroidSigningSettingsObject()
}

context(ProblemReporterContext, ConvertCtx)
internal fun AmperObject.convertAndroidSigningSettingsObject() = AndroidSigningSettings().apply {
    ::enabled.convertChildBoolean()
    ::propertiesFile.convertChildScalar { asAbsolutePath() }
}

context(ProblemReporterContext, ConvertCtx)
internal fun AmperObject.convertKotlinSettings() = KotlinSettings().apply {
    ::languageVersion.convertChildEnum(KotlinVersion)
    ::apiVersion.convertChildEnum(KotlinVersion)

    ::allWarningsAsErrors.convertChildBoolean()
    ::suppressWarnings.convertChildBoolean()
    ::verbose.convertChildBoolean()
    ::debug.convertChildBoolean()
    ::progressiveMode.convertChildBoolean()

    ::freeCompilerArgs.convertChildScalarCollection { textValue }
    ::linkerOpts.convertChildScalarCollection { textValue }
    ::languageFeatures.convertChildScalarCollection { textValue }
    ::optIns.convertChildScalarCollection { textValue  }

    ::serialization.convertChild { value?.convertSerializationSettings() }
}

context(ProblemReporterContext, ConvertCtx)
internal fun AmperValue.convertSerializationSettings() = when (this) {
    is AmperLiteral -> SerializationSettings().apply { ::format.convertSelf { textValue } }
    is AmperObject -> SerializationSettings().apply { ::format.convertChildString() }
    else -> null
}

context(ProblemReporterContext, ConvertCtx)
internal fun AmperProperty.convertComposeSettings() = when (value) {
    is AmperLiteral -> (value as? AmperLiteral)?.run { ComposeSettings().apply { ::enabled.convertSelf { (textValue == "enabled") } } }
    else -> (value as? AmperObject)?.convertComposeSettingsObject()
}

context(ProblemReporterContext, ConvertCtx)
internal fun AmperObject.convertComposeSettingsObject() = ComposeSettings().apply {
    ::enabled.convertChildBoolean()
    ::version.convertChildString()
}

context(ProblemReporterContext, ConvertCtx)
internal fun AmperObject.convertIosSettings() = IosSettings().apply {
    ::teamId.convertChildString()
    ::framework.convertChildValue { (value as? AmperObject)?.convertIosFrameworkSettings() }
}

context(ProblemReporterContext, ConvertCtx)
internal fun AmperObject.convertIosFrameworkSettings() = IosFrameworkSettings().apply {
    ::basename.convertChildString()
//    println("FOO Read basename is $basename")
    ::isStatic.convertChildBoolean()
}

context(ProblemReporterContext, ConvertCtx)
internal fun AmperObject.convertPublishingSettings() = PublishingSettings().apply {
    ::group.convertChildString()
    ::version.convertChildString()
    ::name.convertChildString()
}

context(ProblemReporterContext, ConvertCtx)
internal fun AmperObject.convertKoverSettings() = KoverSettings().apply {
    ::enabled.convertChildBoolean()
    ::xml.convertChildValue { (value as? AmperObject)?.convertKoverXmlSettings() }
    ::html.convertChildValue { (value as? AmperObject)?.convertKoverHtmlSettings() }
}

context(ProblemReporterContext, ConvertCtx)
internal fun AmperObject.convertKoverXmlSettings() = KoverXmlSettings().apply {
    ::onCheck.convertChildBoolean()
    ::reportFile.convertChildScalar { asAbsolutePath() }
}

context(ProblemReporterContext, ConvertCtx)
internal fun AmperObject.convertKoverHtmlSettings() = KoverHtmlSettings().apply {
    ::onCheck.convertChildBoolean()
    ::title.convertChildString()
    ::charset.convertChildString()
    ::reportDir.convertChildScalar { asAbsolutePath() }
}

context(ProblemReporterContext, ConvertCtx)
internal fun AmperObject.convertNativeSettings() = NativeSettings().apply {
    ::entryPoint.convertChildString()
}
