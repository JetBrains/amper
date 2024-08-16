/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi.yaml

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
import org.jetbrains.amper.frontend.schemaConverter.psi.amper.asAbsolutePath
import org.jetbrains.amper.frontend.schemaConverter.psi.amper.convertChildScalar
import org.jetbrains.amper.frontend.schemaConverter.psi.amper.convertChildString
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLValue

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLKeyValue.convertSettings() =
    asMappingNode()?.doConvertSettings()?.applyPsiTrace(this)

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLMapping.doConvertSettings() = Settings().apply {
    ::jvm.convertChildValue { asMappingNode()?.convertJvmSettings() }
    ::android.convertChildValue { asMappingNode()?.convertAndroidSettings() }
    ::kotlin.convertChildValue { asMappingNode()?.convertKotlinSettings() }
    ::compose.convertChildValue { value?.convertComposeSettings() }
    ::ios.convertChildValue { asMappingNode()?.convertIosSettings() }
    ::publishing.convertChildValue { asMappingNode()?.convertPublishingSettings() }
    ::kover.convertChildValue { asMappingNode()?.convertKoverSettings() }
    ::native.convertChildValue { asMappingNode()?.convertNativeSettings() }

    ::junit.convertChildEnum(JUnitVersion)
}

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLMapping.convertJvmSettings() = JvmSettings().apply {
    ::release.convertChildEnum(JavaVersion.Index)
    ::mainClass.convertChildString()
}

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLMapping.convertAndroidSettings() = AndroidSettings().apply {
    ::compileSdk.convertChildEnum(AndroidVersion)
    ::minSdk.convertChildEnum(AndroidVersion)
    ::maxSdk.convertChildEnum(AndroidVersion)
    ::targetSdk.convertChildEnum(AndroidVersion)
    ::applicationId.convertChildString()
    ::namespace.convertChildString()
    ::signing.convertChildValue { value?.convertAndroidSigningSettings() }
}

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLValue.convertAndroidSigningSettings() = when(this) {
    is YAMLScalar -> AndroidSigningSettings().apply { ::enabled.convertSelf { (textValue == "enabled") } }
    is YAMLMapping -> AndroidSigningSettings().apply {
        ::propertiesFile.convertChildScalar { asAbsolutePath() }
    }
    else -> null
}


context(ProblemReporterContext, ConvertCtx)
internal fun YAMLMapping.convertKotlinSettings() = KotlinSettings().apply {
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
internal fun YAMLValue.convertSerializationSettings() = when (this) {
    is YAMLScalar -> SerializationSettings().apply { ::format.convertSelf { textValue } }
    is YAMLMapping -> SerializationSettings().apply { ::format.convertChildString() }
    else -> null
}

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLValue.convertComposeSettings() = when (this) {
    is YAMLScalar -> ComposeSettings().apply { ::enabled.convertSelf { (textValue == "enabled") } }
    is YAMLMapping -> ComposeSettings().apply {
        ::enabled.convertChildBoolean()
        ::version.convertChildString()
    }
    else -> null
}

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLMapping.convertIosSettings() = IosSettings().apply {
    ::teamId.convertChildString()
    ::framework.convertChildValue { asMappingNode()?.convertIosFrameworkSettings() }
}

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLMapping.convertIosFrameworkSettings() = IosFrameworkSettings().apply {
    ::basename.convertChildString()
//    println("FOO Read basename is $basename")
    ::isStatic.convertChildBoolean()
}

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLMapping.convertPublishingSettings() = PublishingSettings().apply {
    ::group.convertChildString()
    ::version.convertChildString()
    ::name.convertChildString()
}

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLMapping.convertKoverSettings() = KoverSettings().apply {
    ::enabled.convertChildBoolean()
    ::xml.convertChildValue { asMappingNode()?.convertKoverXmlSettings() }
    ::html.convertChildValue { asMappingNode()?.convertKoverHtmlSettings() }
}

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLMapping.convertKoverXmlSettings() = KoverXmlSettings().apply {
    ::onCheck.convertChildBoolean()
    ::reportFile.convertChildScalar { asAbsolutePath() }
}

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLMapping.convertKoverHtmlSettings() = KoverHtmlSettings().apply {
    ::onCheck.convertChildBoolean()
    ::title.convertChildString()
    ::charset.convertChildString()
    ::reportDir.convertChildScalar { asAbsolutePath() }
}

context(ProblemReporterContext, ConvertCtx)
internal fun YAMLMapping.convertNativeSettings() = NativeSettings().apply {
    ::entryPoint.convertChildString()
}
