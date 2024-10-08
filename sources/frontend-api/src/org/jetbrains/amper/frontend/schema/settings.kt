/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.AdditionalSchemaDef
import org.jetbrains.amper.frontend.api.ConstructorParameter
import org.jetbrains.amper.frontend.api.PlatformSpecific
import org.jetbrains.amper.frontend.api.ProductTypeSpecific
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.TraceableString
import java.nio.file.Path


@SchemaDoc("JUnit version that is used for the module tests")
enum class JUnitVersion(override val schemaValue: String, override val outdated: Boolean = false) : SchemaEnum {
    JUNIT4("junit-4"),
    JUNIT5("junit-5"),
    NONE("none");
    companion object Index : EnumMap<JUnitVersion, String>(JUnitVersion::values, JUnitVersion::schemaValue)
}

class Settings : SchemaNode() {

    @SchemaDoc("JVM platform-specific settings")
    @PlatformSpecific(Platform.JVM)
    var jvm by value(::JvmSettings)

    @SchemaDoc("Kotlin language and the compiler settings")
    var kotlin by value(::KotlinSettings)

    @SchemaDoc("Android toolchain and platform settings")
    @PlatformSpecific(Platform.ANDROID)
    var android by value(::AndroidSettings)

    @SchemaDoc("[Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) framework." +
            "Read more about [Compose configuration](#configuring-compose-multiplatform)")
    var compose by value(::ComposeSettings)

    @SchemaDoc("JUnit test runner on the JVM and Android platforms. " +
            "Read more about [testing support](#tests)")
    @PlatformSpecific(Platform.JVM, Platform.ANDROID)
    var junit by value(JUnitVersion.JUNIT4)

    @SchemaDoc("iOS toolchain and platform settings")
    @PlatformSpecific(Platform.IOS)
    var ios by value(::IosSettings)

    @SchemaDoc("Publishing settings")
    var publishing by nullableValue<PublishingSettings>()

    @SchemaDoc("Kover settings for code coverage. Read more [about Kover](https://kotlin.github.io/kotlinx-kover/gradle-plugin/)")
    var kover by nullableValue<KoverSettings>()

    @SchemaDoc("Native applications settings")
    @PlatformSpecific(Platform.NATIVE)
    var native by nullableValue<NativeSettings>()
}

@AdditionalSchemaDef(composeSettingsShortForm)
class ComposeSettings : SchemaNode() {

    @SchemaDoc("Enable Compose runtime, dependencies and the compiler plugins")
    var enabled by value(default = false)

    @SchemaDoc("The Compose plugin version")
    var version by nullableValue<String> { UsedVersions.composeVersion.takeIf { enabled } }

    @SchemaDoc("Compose Resources settings")
    var resources by value(::ComposeResourcesSettings)
}

class ComposeResourcesSettings : SchemaNode() {
    @SchemaDoc("Whether to enable Compose resources packaging and accessor code generation.")
    var enabled by value(default = false)

    @SchemaDoc(
        "A unique identifier for the resources in the current module.<br>" +
                "Used as package for the generated Res class and for isolating resources in the final artifact."
    )
    var packageName by value(default = "")

    @SchemaDoc(
        "Whether the generated resources accessors should be exposed to other modules (public) or " +
                "internal."
    )
    var exposedAccessors by value(default = false)
}

const val composeSettingsShortForm = """
  {
    "enum": [
      "enabled"
    ]
  }
"""

@AdditionalSchemaDef(serializationSettingsShortForm)
class SerializationSettings : SchemaNode() {

    @ConstructorParameter
    @SchemaDoc("The Kotlin Serialization format to use, or 'none' if no format should be automatically added")
    var format by value("none")

    @SchemaDoc("The version of the Kotlinx Serialization core and format libraries to use")
    var version by value(UsedVersions.kotlinxSerializationVersion)
}

const val serializationFormatNone = "none"
const val serializationSettingsShortForm = """
  {
    "enum": [
      "json",
      "$serializationFormatNone"
    ]
  }
"""

@AdditionalSchemaDef(parcelizeSettingsShortForm)
class ParcelizeSettings : SchemaNode() {

    @SchemaDoc("Whether to enable Kotlin Parcelize")
    var enabled by value(default = false)

    @SchemaDoc("Full-qualified name of additional annotations that should be considered as @Parcelize")
    var additionalAnnotations: List<TraceableString> by value(default = emptyList())
}

const val parcelizeSettingsShortForm = """
  {
    "enum": [
      "enabled"
    ]
  }
"""

class IosSettings : SchemaNode() {

    @SchemaDoc("A Team ID is a unique string assigned to your team by Apple.<br>" +
            "It's necessary if you want to run/test on a Apple device.<br>" +
            "Read [how to locate your team ID in Xcode](https://developer.apple.com/help/account/manage-your-team/locate-your-team-id/)," +
            " or use [KDoctor tool](https://github.com/Kotlin/kdoctor) (`kdoctor --team-ids`)")
    var teamId by nullableValue<String>()

    @SchemaDoc("(Only for the library [product type](#product-types) " +
            "Configure the generated framework to [share the common code with an Xcode project](https://kotlinlang.org/docs/multiplatform-mobile-understand-project-structure.html#ios-framework)")
    @ProductTypeSpecific(ProductType.LIB)
    var framework by value(::IosFrameworkSettings)
}

class IosFrameworkSettings : SchemaNode() {

    @SchemaDoc("The name of the generated framework")
    var basename by value("kotlin")

    @SchemaDoc("Whether to create a dynamically linked or statically linked framework")
    var isStatic by value(false)
}

class PublishingSettings : SchemaNode() {

    @SchemaDoc("Group ID of the published Maven artifact")
    var group by nullableValue<String>()

    @SchemaDoc("Version of the published Maven artifact")
    var version by nullableValue<String>()

    @SchemaDoc("Artifact ID of the published Maven artifact")
    var name by nullableValue<String>()
}

@AdditionalSchemaDef(koverSettingsShortForm)
class KoverSettings : SchemaNode() {

    @SchemaDoc("Enable code overage with Kover")
    var enabled by value(false)

//    @SchemaDoc("")
    var xml by nullableValue<KoverXmlSettings>()

//    @SchemaDoc("")
    var html by nullableValue<KoverHtmlSettings>()
}

const val koverSettingsShortForm = """
  {
    "enum": [
      "enabled"
    ]
  }
"""

class KoverXmlSettings : SchemaNode() {
//    @SchemaDoc("")
    var onCheck by nullableValue<Boolean>()

//    @SchemaDoc("")
    var reportFile by nullableValue<Path>()
}

class KoverHtmlSettings : SchemaNode() {
//    @SchemaDoc("")
    var title by nullableValue<String>()

//    @SchemaDoc("")
    var charset by nullableValue<String>()

//    @SchemaDoc("")
    var onCheck by nullableValue<Boolean>()

//    @SchemaDoc("")
    var reportDir by nullableValue<Path>()
}

class NativeSettings : SchemaNode() {

    // TODO other options from NativeApplicationPart
    @SchemaDoc("The fully-qualified name of the application's entry point function")
    var entryPoint by nullableValue<String>()
}
