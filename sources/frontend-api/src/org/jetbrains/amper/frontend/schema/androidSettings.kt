/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.SchemaEnum
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
    @SchemaDoc("Android 1.0")
    VERSION_1(1, outdated = true),
    @SchemaDoc("Android 1.1")
    VERSION_2(2, outdated = true),
    @SchemaDoc("Android 1.5, Cupcake")
    VERSION_3(3, outdated = true),
    @SchemaDoc("Android 1.6, Donut")
    VERSION_4(4, outdated = true),
    @SchemaDoc("Android 2.0, Eclair")
    VERSION_5(5, outdated = true),
    @SchemaDoc("Android 2.0.1, Eclair")
    VERSION_6(6, outdated = true),
    @SchemaDoc("Android 2.1, Eclair")
    VERSION_7(7, outdated = true),
    @SchemaDoc("Android 2.2, Froyo")
    VERSION_8(8, outdated = true),
    @SchemaDoc("Android 2.3-2.3.2, Gingerbread")
    VERSION_9(9, outdated = true),
    @SchemaDoc("Android 2.3.3-2.3.7, Gingerbread")
    VERSION_10(10, outdated = true),
    @SchemaDoc("Android 3.0, Honeycomb")
    VERSION_11(11, outdated = true),
    @SchemaDoc("Android 3.1, Honeycomb")
    VERSION_12(12, outdated = true),
    @SchemaDoc("Android 3.2, Honeycomb")
    VERSION_13(13, outdated = true),
    @SchemaDoc("Android 4.0.1-4.0.2, Ice Cream Sandwich")
    VERSION_14(14, outdated = true),
    @SchemaDoc("Android 4.0.3-4.0.4, Ice Cream Sandwich")
    VERSION_15(15, outdated = true),
    @SchemaDoc("Android 4.1, Jelly Bean")
    VERSION_16(16, outdated = true),
    @SchemaDoc("Android 4.2, Jelly Bean")
    VERSION_17(17, outdated = true),
    @SchemaDoc("Android 4.3, Jelly Bean")
    VERSION_18(18, outdated = true),
    @SchemaDoc("Android 4.4, KitKat")
    VERSION_19(19, outdated = true),
    @SchemaDoc("Android 5.0, Lollipop")
    VERSION_20(20, outdated = true),
    @SchemaDoc("Android 5.0, Lollipop")
    VERSION_21(21),
    @SchemaDoc("Android 5.1, Lollipop")
    VERSION_22(22),
    @SchemaDoc("Android 6.0, Marshmallow")
    VERSION_23(23),
    @SchemaDoc("Android 7.0, Nougat")
    VERSION_24(24),
    @SchemaDoc("Android 7.1, Nougat")
    VERSION_25(25),
    @SchemaDoc("Android 8.0, Oreo")
    VERSION_26(26),
    @SchemaDoc("Android 8.1, Oreo")
    VERSION_27(27),
    @SchemaDoc("Android 9, Pie")
    VERSION_28(28),
    @SchemaDoc("Android 10, Q")
    VERSION_29(29),
    @SchemaDoc("Android 11, R")
    VERSION_30(30),
    @SchemaDoc("Android 12, S")
    VERSION_31(31),
    @SchemaDoc("Android 12L, S")
    VERSION_32(32),
    @SchemaDoc("Android 13, Tiramisu")
    VERSION_33(33),
    @SchemaDoc("Android 14, Upside Down Cake")
    VERSION_34(34),
    @SchemaDoc("Android 15, Vanilla Ice Cream")
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
    var targetSdk by dependentValue(::compileSdk)

    @SchemaDoc("The API level to compile the code. The code can use only the Android APIs up to that API level. " +
            "[Read more](https://developer.android.com/reference/tools/gradle-api/com/android/build/api/dsl/CommonExtension#compileSdk())")
    var compileSdk by value(AndroidVersion.VERSION_35)

    @SchemaDoc("A Kotlin or Java package name for the generated `R` and `BuildConfig` classes. " +
            "[Read more](https://developer.android.com/build/configure-app-module#set-namespace)")
    var namespace by value("org.example.namespace")

    @SchemaDoc("The ID for the application on a device and in the Google Play Store. " +
            "[Read more](https://developer.android.com/build/configure-app-module#set-namespace)")
    @ProductTypeSpecific(ProductType.ANDROID_APP)
    var applicationId by dependentValue(::namespace)

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

class AndroidSigningSettings : SchemaNode() {
    @SchemaDoc("Enable signing with keystore")
    var enabled by value(default = false)

    @SchemaDoc("Properties file where the keystore data is stored.")
    var propertiesFile by value<Path> { Path("keystore.properties") }
}

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

