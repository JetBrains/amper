/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.AdditionalSchemaDef
import org.jetbrains.amper.frontend.api.EnumOrderSensitive
import org.jetbrains.amper.frontend.api.ProductTypeSpecific
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.TraceableString
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path

@EnumOrderSensitive(reverse = true)
enum class AndroidVersion(
    val versionNumber: Int,
    override val outdated: Boolean = false
) : SchemaEnum {
    VERSION_1(1, outdated = true),
    VERSION_2(2, outdated = true),
    VERSION_3(3, outdated = true),
    VERSION_4(4, outdated = true),
    VERSION_5(5, outdated = true),
    VERSION_6(6, outdated = true),
    VERSION_7(7, outdated = true),
    VERSION_8(8, outdated = true),
    VERSION_9(9, outdated = true),
    VERSION_10(10, outdated = true),
    VERSION_11(11, outdated = true),
    VERSION_12(12, outdated = true),
    VERSION_13(13, outdated = true),
    VERSION_14(14, outdated = true),
    VERSION_15(15, outdated = true),
    VERSION_16(16, outdated = true),
    VERSION_17(17, outdated = true),
    VERSION_18(18, outdated = true),
    VERSION_19(19, outdated = true),
    VERSION_20(20, outdated = true),
    VERSION_21(21),
    VERSION_22(22),
    VERSION_23(23),
    VERSION_24(24),
    VERSION_25(25),
    VERSION_26(26),
    VERSION_27(27),
    VERSION_28(28),
    VERSION_29(29),
    VERSION_30(30),
    VERSION_31(31),
    VERSION_32(32),
    VERSION_33(33),
    VERSION_34(34),
    VERSION_35(35),
    ;

    override val schemaValue = versionNumber.toString()

    companion object Index : EnumMap<AndroidVersion, String>(AndroidVersion::values, AndroidVersion::schemaValue)
}

class AndroidSettings : SchemaNode() {
    @SchemaDoc("Minimum API level needed to run the application. " +
            "[Read more](https://developer.android.com/guide/topics/manifest/uses-sdk-element.html)")
    var minSdk by value(AndroidVersion.VERSION_21)

    @SchemaDoc("Maximum API level on which the application can run. " +
            "[Read more](https://developer.android.com/guide/topics/manifest/uses-sdk-element.html)")
    var maxSdk by nullableValue<AndroidVersion>()

    @SchemaDoc("The target API level for the application. " +
            "[Read more](https://developer.android.com/guide/topics/manifest/uses-sdk-element.html)")
    var targetSdk by value(AndroidVersion.VERSION_35)

    @SchemaDoc("The API level to compile the code. The code can use only the Android APIs up to that API level. " +
            "[Read more](https://developer.android.com/reference/tools/gradle-api/com/android/build/api/dsl/CommonExtension#compileSdk())")
    var compileSdk by value { targetSdk }

    @SchemaDoc("A Kotlin or Java package name for the generated `R` and `BuildConfig` classes. " +
            "[Read more](https://developer.android.com/build/configure-app-module#set-namespace)")
    var namespace by value("org.example.namespace")

    @SchemaDoc("The ID for the application on a device and in the Google Play Store. " +
            "[Read more](https://developer.android.com/build/configure-app-module#set-namespace)")
    @ProductTypeSpecific(ProductType.ANDROID_APP)
    var applicationId by value { namespace }

    @SchemaDoc("Application signing settings. " +
    "[Read more](https://developer.android.com/studio/publish/app-signing)")
    @ProductTypeSpecific(ProductType.ANDROID_APP)
    var signing by value(::AndroidSigningSettings)

    @SchemaDoc("Version code. " +
            "[Read more](https://developer.android.com/studio/publish/versioning#versioningsettings)")
    @ProductTypeSpecific(ProductType.ANDROID_APP)
    var versionCode by value(1)

    @SchemaDoc("Version name. " +
            "[Read more](https://developer.android.com/studio/publish/versioning#versioningsettings)")
    @ProductTypeSpecific(ProductType.ANDROID_APP)
    var versionName by value("unspecified")

    @SchemaDoc("Configure [Kotlin Parcelize](https://developer.android.com/kotlin/parcelize) to automatically " +
            "implement the `Parcelable` interface for classes annotated with `@Parcelize`.")
    var parcelize by value<ParcelizeSettings>(ParcelizeSettings())
}

@AdditionalSchemaDef(ANDROID_SIGNING_SETTINGS_SHORT_FORM)
class AndroidSigningSettings : SchemaNode() {
    @SchemaDoc("Enable signing with keystore")
    var enabled by value(default = false)

    @SchemaDoc("Properties file where the keystore data is stored.")
    var propertiesFile by value<Path> { Path("keystore.properties") }
}

const val ANDROID_SIGNING_SETTINGS_SHORT_FORM = """
  {
    "enum": [
      "enabled"
    ]
  }
"""

enum class KeystoreProperty(val key: String) {
    StoreFile("storeFile"),
    StorePassword("storePassword"),
    KeyAlias("keyAlias"),
    KeyPassword("keyPassword")
}

val Properties.storeFile: String? get() = getProperty(KeystoreProperty.StoreFile.key)
val Properties.storePassword: String? get() = getProperty(KeystoreProperty.StorePassword.key)
val Properties.keyAlias: String? get() = getProperty(KeystoreProperty.KeyAlias.key)
val Properties.keyPassword: String? get() = getProperty(KeystoreProperty.KeyPassword.key)

@AdditionalSchemaDef(parcelizeSettingsShortForm)
class ParcelizeSettings : SchemaNode() {

    @SchemaDoc("Whether to enable [Parcelize](https://developer.android.com/kotlin/parcelize). When enabled, an " +
            "implementation of the `Parcelable` interface is automatically generated for classes annotated with " +
            "`@Parcelize`.")
    var enabled by value(default = false)

    @SchemaDoc("The full-qualified name of additional annotations that should be considered as `@Parcelize`. " +
            "This is useful if you need to annotate classes in common code shared between different platforms, where " +
            "the real `@Parcelize` annotation is not available. In that case, create your own common annotation and " +
            "add its fully-qualified name here so that Parcelize recognizes it.")
    var additionalAnnotations: List<TraceableString> by value(default = emptyList())
}

const val parcelizeSettingsShortForm = """
  {
    "enum": [
      "enabled"
    ]
  }
"""
