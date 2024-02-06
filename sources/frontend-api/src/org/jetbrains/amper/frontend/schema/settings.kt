/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.AdditionalSchemaDef
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import java.nio.file.Path


@SchemaDoc("JUnit version that is used for the module tests")
enum class JUnitVersion(override val schemaValue: String, override val outdated: Boolean = false) : SchemaEnum {
    JUNIT4("junit-4"),
    JUNIT5("junit-5"),
    NONE("none");
    companion object Index : EnumMap<JUnitVersion, String>(JUnitVersion::values, JUnitVersion::schemaValue)
}

@SchemaDoc("Configures the toolchains used in the build process. See [settings](#settings).")
class Settings : SchemaNode() {

    @SchemaDoc("Java language and the compiler for the JVM platform settings")
    var java by value(::JavaSettings)

    @SchemaDoc("JVM platform specific settings")
    var jvm by value(::JvmSettings)

    @SchemaDoc("Kotlin language and the compiler settings")
    var kotlin by value(::KotlinSettings)

    @SchemaDoc("Android toolchain and platform settings")
    var android by value(::AndroidSettings)

    @SchemaDoc("Compose multiplatform framework settings")
    var compose by value(::ComposeSettings)

    @SchemaDoc("Used JUnit version")
    var junit by value(JUnitVersion.JUNIT4)

    @SchemaDoc("iOS toolchain and platform settings")
    var ios by value(::IosSettings)

    @SchemaDoc("Artifact publishing related settings")
    var publishing by nullableValue<PublishingSettings>()

    @SchemaDoc("Kover settings")
    var kover by nullableValue<KoverSettings>()

    @SchemaDoc("Native applications related settings")
    var native by nullableValue<NativeSettings>()
}

@AdditionalSchemaDef(composeSettingsShortForm)
class ComposeSettings : SchemaNode() {

    @SchemaDoc("Enable Compose runtime, dependencies and the compiler plugins")
    var enabled by value(default = false)

    @SchemaDoc("Used compose version")
    var version by nullableValue<String> { UsedVersions.composeVersion.takeIf { enabled } }
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

    @SchemaDoc("Chosen serialization engine")
    var format by value("json")
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

class IosSettings : SchemaNode() {

    @SchemaDoc("A Team ID is a unique string assigned to your team by Apple.<br>" +
            "It's necessary if you want to run/test on a Apple device.<br>" +
            "Read [how to locate your team ID in Xcode](https://developer.apple.com/help/account/manage-your-team/locate-your-team-id/), or use [KDoctor tool](https://github.com/Kotlin/kdoctor) (`kdoctor --team-ids`)")
    var teamId by nullableValue<String>()

    @SchemaDoc("(Only for the library [product type](Documentation.md#product-types) " +
            "Configure the generated framework to [share the common code with an Xcode project](https://kotlinlang.org/docs/multiplatform-mobile-understand-project-structure.html#ios-framework)")
    var framework by value(::IosFrameworkSettings)
}

class IosFrameworkSettings : SchemaNode() {

    @SchemaDoc("The name of the generated framework")
    var basename by value("kotlin")

    @SchemaDoc("Whether to create a dynamically linked or statically linked framework")
    var isStatic by value(false)
}

class PublishingSettings : SchemaNode() {

    @SchemaDoc("Group id of the published module")
    var group by nullableValue<String>()

    @SchemaDoc("Version of the published module")
    var version by nullableValue<String>()
}

@AdditionalSchemaDef(koverSettingsShortForm)
class KoverSettings : SchemaNode() {

    @SchemaDoc("Enable kover library")
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
    @SchemaDoc("Fqn of the function that is an entry of the application")
    var entryPoint by nullableValue<String>()
}
