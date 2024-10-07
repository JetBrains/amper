/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.api.applyPsiTrace
import org.jetbrains.amper.frontend.schema.AndroidSettings
import org.jetbrains.amper.frontend.schema.AndroidSigningSettings
import org.jetbrains.amper.frontend.schema.AndroidVersion
import org.jetbrains.amper.frontend.schema.CatalogKspProcessorDeclaration
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
import org.jetbrains.amper.frontend.schema.MavenKspProcessorDeclaration
import org.jetbrains.amper.frontend.schema.ModuleKspProcessorDeclaration
import org.jetbrains.amper.frontend.schema.NativeSettings
import org.jetbrains.amper.frontend.schema.KspProcessorDeclaration
import org.jetbrains.amper.frontend.schema.ParcelizeSettings
import org.jetbrains.amper.frontend.schema.PublishingSettings
import org.jetbrains.amper.frontend.schema.SerializationSettings
import org.jetbrains.amper.frontend.schema.Settings

context(Converter)
internal fun MappingEntry.convertSettings() =
    value?.asMappingNode()?.doConvertSettings()?.applyPsiTrace(sourceElement)

context(Converter)
internal fun MappingNode.doConvertSettings() = Settings().also {
    convertChildMapping(it::jvm) { convertJvmSettings() }
    convertChildMapping(it::android) { convertAndroidSettings() }
    convertChildMapping(it::kotlin) { convertKotlinSettings() }
    convertChildValue(it::compose) { convertComposeSettings() }
    convertChildMapping(it::ios) { convertIosSettings() }
    convertChildMapping(it::publishing) { convertPublishingSettings() }
    convertChildMapping(it::kover) { convertKoverSettings() }
    convertChildMapping(it::native) { convertNativeSettings() }
    convertChildEnum(it::junit, JUnitVersion)
}

context(Converter)
internal fun MappingNode.convertJvmSettings() = JvmSettings().apply {
    convertChildEnum(::release, JavaVersion.Index)
    convertChildString(::mainClass)
}

context(Converter)
internal fun MappingNode.convertAndroidSettings() = AndroidSettings().apply {
    convertChildEnum(::compileSdk, AndroidVersion)
    convertChildEnum(::minSdk, AndroidVersion)
    convertChildEnum(::maxSdk, AndroidVersion)
    convertChildEnum(::targetSdk, AndroidVersion)
    convertChildString(::applicationId)
    convertChildString(::namespace)
    convertChildValue(::signing) { convertAndroidSigningSettings() }
    convertChildInt(::versionCode)
    convertChildString(::versionName)
}

context(Converter)
internal fun PsiElement.convertAndroidSigningSettings() =
    AndroidSigningSettings().apply {
        asMappingNode()?.apply {
            convertChildBoolean(::enabled)
            convertChildScalar(::propertiesFile) { asAbsolutePath() }
        }
        asScalarNode()?.apply {
            convertSelf(::enabled) { (textValue == "enabled") }
        }
    }

context(Converter)
internal fun MappingNode.convertKotlinSettings() = KotlinSettings().apply {
    convertChildEnum(::languageVersion, KotlinVersion)
    convertChildEnum(::apiVersion, KotlinVersion)

    convertChildBoolean(::allWarningsAsErrors)
    convertChildBoolean(::suppressWarnings)
    convertChildBoolean(::verbose)
    convertChildBoolean(::debug)
    convertChildBoolean(::progressiveMode)

    convertChildScalarCollection(::freeCompilerArgs) { asTraceableString() }
    convertChildScalarCollection(::linkerOpts) { asTraceableString() }
    convertChildScalarCollection(::languageFeatures) { asTraceableString() }
    convertChildScalarCollection(::optIns) { asTraceableString() }

    convertChildMapping(::ksp) { convertKspSettings() }

    convertChild(::serialization) { value?.convertSerializationSettings() }
    convertChild(::parcelize) { value?.convertParcelizeSettings() }
}

context(Converter)
internal fun PsiElement.convertSerializationSettings() =
    SerializationSettings().apply {
        asMappingNode()?.apply {
            convertChildString(::format)
            convertChildString(::version)
        }
        asScalarNode()?.apply {
            convertSelf(::format) { textValue }
        }
    }

context(Converter)
internal fun PsiElement.convertParcelizeSettings() =
    ParcelizeSettings().apply {
        asMappingNode()?.apply {
            convertChildBoolean(::enabled)
            convertChildScalarCollection(::additionalAnnotations) { asTraceableString() }
        }
        asScalarNode()?.apply {
            convertSelf(::enabled) { (textValue == "enabled") }
        }
    }

context(Converter)
internal fun PsiElement.convertComposeSettings() =
    ComposeSettings().apply {
        asMappingNode()?.apply {
            convertChildBoolean(::enabled)
            convertChildString(::version)
            convertChildMapping(::resources) { convertComposeResourcesSettings() }
        }
        asScalarNode()?.apply {
            convertSelf(::enabled) { (textValue == "enabled") }
        }
    }

context(Converter)
internal fun MappingNode.convertComposeResourcesSettings() =
    ComposeResourcesSettings().apply {
        convertChildBoolean(::exposedAccessors)
        convertChildString(::packageName)
        convertChildBoolean(::enabled)
    }


context(Converter)
internal fun MappingNode.convertKspSettings() = KspSettings().apply {
    convertChildString(::version)
    convertChildScalarCollection(::processors) { convertKspProcessor() }
    convertChildMapping(::processorOptions) { convertTraceableStringMap() }
}

context(Converter)
internal fun Scalar.convertKspProcessor(): KspProcessorDeclaration = when {
    textValue.startsWith("$") -> CatalogKspProcessorDeclaration(asTraceableString { it.removePrefix("$") })
    textValue.startsWith(".") -> ModuleKspProcessorDeclaration(asTraceableAbsolutePath())
    else -> MavenKspProcessorDeclaration(asTraceableString())
}

context(Converter)
internal fun MappingNode.convertIosSettings() = IosSettings().apply {
    convertChildString(::teamId)
    convertChildMapping(::framework) { convertIosFrameworkSettings() }
}

context(Converter)
internal fun MappingNode.convertIosFrameworkSettings() = IosFrameworkSettings().apply {
    convertChildString(::basename)
    convertChildBoolean(::isStatic)
}

context(Converter)
internal fun MappingNode.convertPublishingSettings() = PublishingSettings().apply {
    convertChildString(::group)
    convertChildString(::version)
    convertChildString(::name)
}

context(Converter)
internal fun MappingNode.convertKoverSettings() = KoverSettings().apply {
    convertChildBoolean(::enabled)
    convertChildMapping(::xml) { convertKoverXmlSettings() }
    convertChildMapping(::html) { convertKoverHtmlSettings() }
}

context(Converter)
internal fun MappingNode.convertKoverXmlSettings() = KoverXmlSettings().apply {
    convertChildBoolean(::onCheck)
    convertChildScalar(::reportFile) { asAbsolutePath() }
}

context(Converter)
internal fun MappingNode.convertKoverHtmlSettings() = KoverHtmlSettings().apply {
    convertChildBoolean(::onCheck)
    convertChildString(::title)
    convertChildString(::charset)
    convertChildScalar(::reportDir) { asAbsolutePath() }
}

context(Converter)
internal fun MappingNode.convertNativeSettings() = NativeSettings().apply {
    convertChildString(::entryPoint)
}
