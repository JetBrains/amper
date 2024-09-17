/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi.yaml

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.api.applyPsiTrace
import org.jetbrains.amper.frontend.schema.AndroidSettings
import org.jetbrains.amper.frontend.schema.AndroidSigningSettings
import org.jetbrains.amper.frontend.schema.AndroidVersion
import org.jetbrains.amper.frontend.schema.ComposeResourcesSettings
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
import org.jetbrains.amper.frontend.schema.KspSettings
import org.jetbrains.amper.frontend.schema.NativeSettings
import org.jetbrains.amper.frontend.schema.PublishingSettings
import org.jetbrains.amper.frontend.schema.SerializationSettings
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.schemaConverter.psi.ConvertCtx

context(ProblemReporterContext, ConvertCtx)
internal fun PsiElement.convertSettings() =
    asMappingNode()?.doConvertSettings()?.applyPsiTrace(this)

context(ProblemReporterContext, ConvertCtx)
internal fun MappingNode.doConvertSettings() = Settings().apply {
    ::jvm.convertChildValue { asMappingNode()?.convertJvmSettings() }
    ::android.convertChildValue { asMappingNode()?.convertAndroidSettings() }
    ::kotlin.convertChildValue { asMappingNode()?.convertKotlinSettings() }
    ::compose.convertChildValue { value?.convertComposeSettings() }
    ::ksp.convertChildValue { asMappingNode()?.convertKspSettings() }
    ::ios.convertChildValue { asMappingNode()?.convertIosSettings() }
    ::publishing.convertChildValue { asMappingNode()?.convertPublishingSettings() }
    ::kover.convertChildValue { asMappingNode()?.convertKoverSettings() }
    ::native.convertChildValue { asMappingNode()?.convertNativeSettings() }

    ::junit.convertChildEnum(JUnitVersion)
}

context(ProblemReporterContext, ConvertCtx)
internal fun MappingNode.convertJvmSettings() = JvmSettings().apply {
    ::release.convertChildEnum(JavaVersion.Index)
    ::mainClass.convertChildString()
}

context(ProblemReporterContext, ConvertCtx)
internal fun MappingNode.convertAndroidSettings() = AndroidSettings().apply {
    ::compileSdk.convertChildEnum(AndroidVersion)
    ::minSdk.convertChildEnum(AndroidVersion)
    ::maxSdk.convertChildEnum(AndroidVersion)
    ::targetSdk.convertChildEnum(AndroidVersion)
    ::applicationId.convertChildString()
    ::namespace.convertChildString()
    ::signing.convertChildValue { value?.convertAndroidSigningSettings() }
    ::versionCode.convertChildInt()
    ::versionName.convertChildString()
}

context(ProblemReporterContext, ConvertCtx)
internal fun PsiElement.convertAndroidSigningSettings() =
    this.asMappingNode()?.let {
        with(it) {
            AndroidSigningSettings().apply {
                ::enabled.convertChildBoolean()
                ::propertiesFile.convertChildScalar { asAbsolutePath() }
            }
        }
    } ?: this.asScalarNode()?.let {
        with(it) {
            AndroidSigningSettings().apply { ::enabled.convertSelf { (textValue == "enabled") } }
        }
    }

context(ProblemReporterContext, ConvertCtx)
internal fun MappingNode.convertKotlinSettings() = KotlinSettings().apply {
    ::languageVersion.convertChildEnum(KotlinVersion)
    ::apiVersion.convertChildEnum(KotlinVersion)

    ::allWarningsAsErrors.convertChildBoolean()
    ::suppressWarnings.convertChildBoolean()
    ::verbose.convertChildBoolean()
    ::debug.convertChildBoolean()
    ::progressiveMode.convertChildBoolean()

    ::freeCompilerArgs.convertChildScalarCollection { asTraceableString() }
    ::linkerOpts.convertChildScalarCollection { asTraceableString() }
    ::languageFeatures.convertChildScalarCollection { asTraceableString() }
    ::optIns.convertChildScalarCollection { asTraceableString() }

    ::serialization.convertChild { value?.convertSerializationSettings() }
}

context(ProblemReporterContext, ConvertCtx)
internal fun PsiElement.convertSerializationSettings() =
    this.asMappingNode()?.let {
        with(it) {
            SerializationSettings().apply { ::format.convertChildString() }
        }
    } ?: this.asScalarNode()?.let {
        with(it) {
            SerializationSettings().apply { ::format.convertSelf { textValue } }
        }
    }

context(ProblemReporterContext, ConvertCtx)
internal fun PsiElement.convertComposeSettings() =
    this.asMappingNode()?.let {
        with(it) {
            ComposeSettings().apply {
                ::enabled.convertChildBoolean()
                ::version.convertChildString()
                ::resources.convertChildValue { convertComposeResourcesSettings() }
            }
        }
    } ?: this.asScalarNode()?.let {
        with(it) {
            ComposeSettings().apply { ::enabled.convertSelf { (textValue == "enabled") } }
        }
    }

context(ProblemReporterContext, ConvertCtx)
internal fun MappingEntry.convertComposeResourcesSettings() =
    value?.asMappingNode()?.also {
        ComposeResourcesSettings().apply {
            ::exposedAccessors.convertChildBoolean()
            ::packageName.convertChildString()
            ::enabled.convertChildBoolean()
        }
    }

context(ProblemReporterContext, ConvertCtx)
internal fun MappingNode.convertKspSettings() = KspSettings().apply {
    ::version.convertChildString()
    ::processors.convertChildScalarCollection { asTraceableString() }
    ::processorOptions.convertChildValue { asMappingNode()?.convertTraceableStringMap() }
}

context(ProblemReporterContext, ConvertCtx)
internal fun MappingNode.convertIosSettings() = IosSettings().apply {
    ::teamId.convertChildString()
    ::framework.convertChildValue { asMappingNode()?.convertIosFrameworkSettings() }
}

context(ProblemReporterContext, ConvertCtx)
internal fun MappingNode.convertIosFrameworkSettings() = IosFrameworkSettings().apply {
    ::basename.convertChildString()
//    println("FOO Read basename is $basename")
    ::isStatic.convertChildBoolean()
}

context(ProblemReporterContext, ConvertCtx)
internal fun MappingNode.convertPublishingSettings() = PublishingSettings().apply {
    ::group.convertChildString()
    ::version.convertChildString()
    ::name.convertChildString()
}

context(ProblemReporterContext, ConvertCtx)
internal fun MappingNode.convertKoverSettings() = KoverSettings().apply {
    ::enabled.convertChildBoolean()
    ::xml.convertChildValue { asMappingNode()?.convertKoverXmlSettings() }
    ::html.convertChildValue { asMappingNode()?.convertKoverHtmlSettings() }
}

context(ProblemReporterContext, ConvertCtx)
internal fun MappingNode.convertKoverXmlSettings() = KoverXmlSettings().apply {
    ::onCheck.convertChildBoolean()
    ::reportFile.convertChildScalar { asAbsolutePath() }
}

context(ProblemReporterContext, ConvertCtx)
internal fun MappingNode.convertKoverHtmlSettings() = KoverHtmlSettings().apply {
    ::onCheck.convertChildBoolean()
    ::title.convertChildString()
    ::charset.convertChildString()
    ::reportDir.convertChildScalar { asAbsolutePath() }
}

context(ProblemReporterContext, ConvertCtx)
internal fun MappingNode.convertNativeSettings() = NativeSettings().apply {
    ::entryPoint.convertChildString()
}
