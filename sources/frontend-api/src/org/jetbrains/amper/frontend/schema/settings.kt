/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.Aliases
import org.jetbrains.amper.frontend.api.GradleSpecific
import org.jetbrains.amper.frontend.api.KnownStringValues
import org.jetbrains.amper.frontend.api.PlatformAgnostic
import org.jetbrains.amper.frontend.api.PlatformSpecific
import org.jetbrains.amper.frontend.api.ProductTypeSpecific
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Shorthand
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
    @PlatformSpecific(Platform.JVM, Platform.ANDROID)
    var jvm by value(::JvmSettings)

    @SchemaDoc("Java language and the compiler settings")
    @PlatformSpecific(Platform.JVM, Platform.ANDROID)
    var java by value(::JavaSettings)

    @SchemaDoc("Kotlin language and the compiler settings")
    var kotlin by value(::KotlinSettings)

    @SchemaDoc("Android toolchain and platform settings")
    @PlatformSpecific(Platform.ANDROID)
    var android by value(::AndroidSettings)

    @PlatformAgnostic
    @SchemaDoc("[Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) framework." +
            "Read more about [Compose configuration](#configuring-compose-multiplatform)")
    var compose by value(::ComposeSettings)

    @Aliases("test")
    @SchemaDoc("JUnit test runner on the JVM and Android platforms. " +
            "Read more about [testing support](#tests)")
    @PlatformSpecific(Platform.JVM, Platform.ANDROID)
    var junit by value(JUnitVersion.JUNIT5)

    @SchemaDoc("iOS toolchain and platform settings")
    @PlatformSpecific(Platform.IOS)
    var ios by value(::IosSettings)

    @SchemaDoc("Publishing settings")
    var publishing by nullableValue<PublishingSettings>()

    @GradleSpecific("kover is not yet supported")
    @Aliases("coverage")
    @SchemaDoc("Kover settings for code coverage. Read more [about Kover](https://kotlin.github.io/kotlinx-kover/gradle-plugin/)")
    var kover by nullableValue<KoverSettings>()

    @SchemaDoc("Native applications settings")
    @PlatformSpecific(Platform.NATIVE)
    var native by nullableValue<NativeSettings>()

    @SchemaDoc("Ktor server settings")
    var ktor by value(::KtorSettings)

    @PlatformSpecific(Platform.JVM)
    @SchemaDoc("Spring Boot settings")
    var springBoot by value(::SpringBootSettings)

    @PlatformSpecific(Platform.JVM, Platform.ANDROID)
    @SchemaDoc("Lombok settings")
    var lombok by value(::LombokSettings)
}

class ComposeSettings : SchemaNode() {

    @Shorthand
    @SchemaDoc("Enable Compose runtime, dependencies and the compiler plugins")
    var enabled by value(default = false)

    @SchemaDoc("The Compose plugin version")
    var version by value(UsedVersions.composeVersion)

    @SchemaDoc("Compose Resources settings")
    var resources by value(::ComposeResourcesSettings)

    @SchemaDoc("Experimental Compose settings")
    var experimental by value(::ComposeExperimentalSettings)
}

class ComposeResourcesSettings : SchemaNode() {
    
    @Deprecated("Need to remove its usage in external projects first")
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

class ComposeExperimentalSettings: SchemaNode() {
    
    @SchemaDoc("Experimental Compose hot-reload settings")
    var hotReload by value(::ComposeExperimentalHotReloadSettings)
}

class ComposeExperimentalHotReloadSettings: SchemaNode() {
    
    @Shorthand
    @SchemaDoc("Enable hot reload")
    var enabled by value(default = false)
}

class SerializationSettings : SchemaNode() {

    @Shorthand
    @SchemaDoc("Enables the kotlinx.serialization compiler plugin, which generates code based on " +
            "@Serializable annotations. This also automatically adds the kotlinx-serialization-core library to " +
            "provide the annotations and facilities for serialization, but no specific serialization format.")
    // if a format is specified, we need to enable serialization (mostly to be backwards compatible)
    var enabled by dependentValue(::format, "Enabled when 'format' is specified") { it != null }

    @Shorthand
    @SchemaDoc("The [kotlinx.serialization format](https://github.com/Kotlin/kotlinx.serialization/blob/master/formats/README.md) " +
            "to use, such as `json`. When set, the corresponding `kotlinx-serialization-<format>` library is " +
            "automatically added to dependencies. When null, no format dependency is automatically added. " +
            "Prefer using the built-in catalog dependencies for this, as it gives control over the 'scope' and " +
            "'exported' properties.")
    @KnownStringValues("json", "json-okio", "hocon", "protobuf", "cbor", "properties", "none")
    var format by nullableValue<String>(default = null)

    @SchemaDoc("The version of the kotlinx.serialization core and format libraries to use.")
    var version by value(default = UsedVersions.kotlinxSerializationVersion)
}

const val legacySerializationFormatNone = "none"

class IosSettings : SchemaNode() {

    @GradleSpecific("the team ID is managed in the Xcode project")
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

    @GradleSpecific(message = "the framework name is always `KotlinModules` in Amper")
    @SchemaDoc("The name of the generated framework")
    var basename by value("kotlin")

    @GradleSpecific(message = "Amper uses static framework linking")
    @SchemaDoc("Whether to create a dynamically linked or statically linked framework")
    var isStatic by value(false)
}

class PublishingSettings : SchemaNode() {

    @SchemaDoc("Group ID of the published Maven artifact")
    var group by nullableValue<String>()

    @SchemaDoc("Version of the published Maven artifact")
    var version by nullableValue<String>()

    @Aliases("artifact")
    @SchemaDoc("Artifact ID of the published Maven artifact")
    var name by nullableValue<String>()
}

class KoverSettings : SchemaNode() {

    @Shorthand
    @SchemaDoc("Enable code overage with Kover")
    var enabled by value(false)

//    @SchemaDoc("")
    var xml by nullableValue<KoverXmlSettings>()

//    @SchemaDoc("")
    var html by nullableValue<KoverHtmlSettings>()
}

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

class KtorSettings: SchemaNode() {

    @Shorthand
    @SchemaDoc("Enable Ktor server")
    var enabled by value(default = false)

    @SchemaDoc("Ktor version")
    var version by value(default = UsedVersions.ktorVersion)
}

class SpringBootSettings: SchemaNode() {

    @Shorthand
    @SchemaDoc("Enable Spring Boot")
    var enabled by value(default = false)

    @SchemaDoc("Spring Boot version")
    var version by value(default = UsedVersions.springBootVersion)
}

class LombokSettings: SchemaNode() {
    
    @Shorthand
    @SchemaDoc("Enable Lombok")
    var enabled by value(default = false)
}
