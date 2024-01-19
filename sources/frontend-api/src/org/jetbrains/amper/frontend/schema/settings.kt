/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.AdditionalSchemaDef
import org.jetbrains.amper.frontend.api.CustomSchemaDef
import org.jetbrains.amper.frontend.api.SchemaNode
import java.nio.file.Path


enum class JUnitVersion(override val schemaValue: String, override val outdated: Boolean = false) : SchemaEnum {
    JUNIT4("junit-4"),
    JUNIT5("junit-5"),
    NONE("none");
    companion object Index : EnumMap<JUnitVersion, String>(JUnitVersion::values, JUnitVersion::schemaValue)
}

class Settings : SchemaNode() {
    var java by value(::JavaSettings)
    var jvm by value(::JvmSettings)
    var kotlin by value(::KotlinSettings)
    var android by value(::AndroidSettings)
    var compose by nullableValue<ComposeSettings>()
    var junit by value(JUnitVersion.JUNIT4)
    var ios by value(::IosSettings)
    var publishing by nullableValue<PublishingSettings>()
    var kover by nullableValue<KoverSettings>()
    var native by nullableValue<NativeSettings>()
}

@AdditionalSchemaDef(composeSettingsShortForm)
class ComposeSettings : SchemaNode() {
    var enabled by value(false)
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
    var engine by value("json")
}

const val serializationSettingsShortForm = """
  {
    "enum": [
      "json",
      "none"
    ]
  }
"""

class IosSettings : SchemaNode() {
    var teamId by nullableValue<String>()
    var framework by value(::IosFrameworkSettings)
}

class IosFrameworkSettings : SchemaNode() {
    var basename by value("kotlin")
    var isStatic by value(false)
}

class PublishingSettings : SchemaNode() {
    var group by nullableValue<String>()
    var version by nullableValue<String>()
}

@AdditionalSchemaDef(koverSettingsShortForm)
class KoverSettings : SchemaNode() {
    var enabled by value(false)
    var xml by nullableValue<KoverXmlSettings>()
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
    var onCheck by nullableValue<Boolean>()
    var reportFile by nullableValue<Path>()
}

class KoverHtmlSettings : SchemaNode() {
    var title by nullableValue<String>()
    var charset by nullableValue<String>()
    var onCheck by nullableValue<Boolean>()
    var reportDir by nullableValue<Path>()
}

class NativeSettings : SchemaNode() {
    // TODO other options from NativeApplicationPart
    var entryPoint by nullableValue<String>()
}
