/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.GradleSpecific
import org.jetbrains.amper.frontend.api.HiddenFromCompletion
import org.jetbrains.amper.frontend.api.KnownStringValues
import org.jetbrains.amper.frontend.api.Misnomers
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

    // Some stuff in here (JDK) can be used for native as well, so it's not specific to JVM and Android.
    // It's also not platform-agnostic as a whole because some things in it can be set differently (e.g. main class or
    // tests) on JVM and Android.
    @SchemaDoc("Settings that apply to all JVM-related sources (both Java and Kotlin)")
    val jvm: JvmSettings by nested()

    @SchemaDoc("Settings to configure the compilation of Java sources")
    @PlatformSpecific(Platform.JVM, Platform.ANDROID)
    val java: JavaSettings by nested()

    @SchemaDoc("Settings to configure the compilation of Kotlin sources")
    val kotlin: KotlinSettings by nested()

    @SchemaDoc("Android toolchain and platform settings")
    @PlatformSpecific(Platform.ANDROID)
    val android: AndroidSettings by nested()

    @PlatformAgnostic
    @SchemaDoc("[Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) framework. " +
            "Read more about [Compose configuration](#configuring-compose-multiplatform)")
    val compose: ComposeSettings by nested()

    @Misnomers("test")
    @SchemaDoc("JUnit test runner on the JVM and Android platforms. " +
            "Read more about [testing support](#tests)")
    @PlatformSpecific(Platform.JVM, Platform.ANDROID)
    val junit by value(JUnitVersion.JUNIT5)

    @SchemaDoc("iOS toolchain and platform settings")
    @PlatformSpecific(Platform.IOS)
    val ios: IosSettings by nested()

    @SchemaDoc("Publishing settings")
    val publishing: PublishingSettings by nested()

    @GradleSpecific("kover is not yet supported")
    @Misnomers("coverage")
    @SchemaDoc("Kover settings for code coverage. Read more [about Kover](https://kotlin.github.io/kotlinx-kover/gradle-plugin/)")
    val kover by nullableValue<KoverSettings>()

    @SchemaDoc("Native applications settings")
    @PlatformSpecific(Platform.NATIVE)
    @ProductTypeSpecific(ProductType.MACOS_APP, ProductType.LINUX_APP, ProductType.WINDOWS_APP)
    val native by nullableValue<NativeSettings>()

    @SchemaDoc("Ktor server settings")
    val ktor: KtorSettings by nested()

    @PlatformSpecific(Platform.JVM)
    @SchemaDoc("Spring Boot settings")
    val springBoot: SpringBootSettings by nested()

    @PlatformSpecific(Platform.JVM, Platform.ANDROID)
    @SchemaDoc("Lombok settings")
    val lombok: LombokSettings by nested()

    /** no documentation here - the block with Amper internal undesigned settings */
    @PlatformAgnostic
    @HiddenFromCompletion
    val internal: InternalSettings by nested()
}

/**
 * All the plugin settings are linked under here, dynamically.
 * The properties are plugin IDs that are made available in the project.
 *
 * @see org.jetbrains.amper.plugins.schema.model.PluginData
 */
class PluginSettings : SchemaNode()

class ComposeSettings : SchemaNode() {

    @Shorthand
    @SchemaDoc("Enables the Compose compiler plugin, runtime dependency, and library catalog")
    val enabled by value(default = false)

    @SchemaDoc("The Compose plugin version")
    val version by value(DefaultVersions.compose)

    @SchemaDoc("Compose Resources settings")
    val resources: ComposeResourcesSettings by nested()

    @SchemaDoc("Experimental Compose settings")
    val experimental: ComposeExperimentalSettings by nested()
}

class ComposeResourcesSettings : SchemaNode() {
    
    @Deprecated("Need to remove its usage in external projects first")
    val enabled by value(default = false)
    
    @SchemaDoc(
        "A unique identifier for the resources in the current module.<br>" +
                "Used as package for the generated Res class and for isolating resources in the final artifact."
    )
    val packageName by value(default = "")

    @SchemaDoc(
        "Whether the generated resources accessors should be exposed to other modules (public) or " +
                "internal."
    )
    val exposedAccessors by value(default = false)
}

class ComposeExperimentalSettings: SchemaNode() {

    @PlatformSpecific(Platform.JVM) // we can only use Hot Reload on JVM for now, better warn users about it
    @SchemaDoc("Experimental Compose hot-reload settings")
    val hotReload: ComposeExperimentalHotReloadSettings by nested()
}

class ComposeExperimentalHotReloadSettings: SchemaNode() {
    @HiddenFromCompletion
    @SchemaDoc("The version of the Compose Hot Reload toolchain to use.")
    val version by value(default = DefaultVersions.composeHotReload)
}

class SerializationSettings : SchemaNode() {

    @Shorthand
    @SchemaDoc("Enables the kotlinx.serialization compiler plugin, which generates code based on " +
            "@Serializable annotations. This also automatically adds the kotlinx-serialization-core library to " +
            "provide the annotations and facilities for serialization, but no specific serialization format.")
    // if a format is specified, we need to enable serialization (mostly to be backwards compatible)
    val enabled by dependentValue(::format, "enabled when 'format' is specified") { it != null }

    @Shorthand
    @SchemaDoc("The [kotlinx.serialization format](https://github.com/Kotlin/kotlinx.serialization/blob/master/formats/README.md) " +
            "to use, such as `json`. When set, the corresponding `kotlinx-serialization-<format>` library is " +
            "automatically added to dependencies. When null, no format dependency is automatically added. " +
            "Prefer using the built-in catalog dependencies for this, as it gives control over the 'scope' and " +
            "'exported' properties.")
    @KnownStringValues("json", "json-okio", "hocon", "protobuf", "cbor", "properties", "none")
    val format by nullableValue<String>(default = null)

    @SchemaDoc("The version of the kotlinx.serialization core and format libraries to use.")
    val version by value(default = DefaultVersions.kotlinxSerialization)
}

const val legacySerializationFormatNone = "none"

class IosSettings : SchemaNode() {

    @GradleSpecific("the team ID is managed in the Xcode project")
    @SchemaDoc("A Team ID is a unique string assigned to your team by Apple.<br>" +
            "It's necessary if you want to run/test on a Apple device.<br>" +
            "Read [how to locate your team ID in Xcode](https://developer.apple.com/help/account/manage-your-team/locate-your-team-id/)," +
            " or use [KDoctor tool](https://github.com/Kotlin/kdoctor) (`kdoctor --team-ids`)")
    val teamId by nullableValue<String>()

    @SchemaDoc("(Only for the library [product type](#product-types) " +
            "Configure the generated framework to [share the common code with an Xcode project](https://kotlinlang.org/docs/multiplatform-mobile-understand-project-structure.html#ios-framework)")
    @ProductTypeSpecific(ProductType.LIB)
    val framework: IosFrameworkSettings by nested()
}

class IosFrameworkSettings : SchemaNode() {

    @GradleSpecific(message = "the framework name is always `KotlinModules` in Amper")
    @SchemaDoc("The name of the generated framework")
    val basename by value("kotlin")

    @GradleSpecific(message = "Amper uses static framework linking")
    @SchemaDoc("Whether to create a dynamically linked or statically linked framework")
    val isStatic by value(false)
}

class PublishingSettings : SchemaNode() {

    @PlatformAgnostic
    @SchemaDoc("Enables the publication of the module to Maven repositories (via `./amper publish`)")
    val enabled by value(default = false)

    @PlatformAgnostic
    @SchemaDoc("Group ID of the published Maven artifact")
    val group by nullableValue<String>()

    @PlatformAgnostic
    @SchemaDoc("Version of the published Maven artifact")
    val version by nullableValue<String>()

    @Misnomers("artifact")
    @SchemaDoc("Artifact ID of the published Maven artifact")
    val name by nullableValue<String>()
}

class KoverSettings : SchemaNode() {

    @Shorthand
    @SchemaDoc("Enables code overage with Kover")
    val enabled by value(false)

//    @SchemaDoc("")
    val xml by nullableValue<KoverXmlSettings>()

//    @SchemaDoc("")
    val html by nullableValue<KoverHtmlSettings>()
}

class KoverXmlSettings : SchemaNode() {
//    @SchemaDoc("")
    val onCheck by nullableValue<Boolean>()

//    @SchemaDoc("")
    val reportFile by nullableValue<Path>()
}

class KoverHtmlSettings : SchemaNode() {
//    @SchemaDoc("")
    val title by nullableValue<String>()

//    @SchemaDoc("")
    val charset by nullableValue<String>()

//    @SchemaDoc("")
    val onCheck by nullableValue<Boolean>()

//    @SchemaDoc("")
    val reportDir by nullableValue<Path>()
}

class NativeSettings : SchemaNode() {

    // TODO other options from NativeApplicationPart
    @SchemaDoc("The fully-qualified name of the application's entry point function")
    val entryPoint by nullableValue<String>()
}

class KtorSettings: SchemaNode() {

    @Shorthand
    @SchemaDoc("Enables Ktor server")
    val enabled by value(default = false)

    @SchemaDoc("The Ktor version, which is used for the BOM and in the generated library catalog entries")
    val version by value(default = DefaultVersions.ktor)

    @SchemaDoc("Whether to apply the Ktor BOM or not")
    val applyBom by value(default = true)
}

class SpringBootSettings: SchemaNode() {

    @Shorthand
    @SchemaDoc("Enables Spring Boot")
    val enabled by value(default = false)

    @SchemaDoc("The Spring Boot version, which is used for the BOM and in the generated library catalog entries")
    val version by value(default = DefaultVersions.springBoot)

    @SchemaDoc("Whether to apply the spring-boot-dependencies BOM or not")
    val applyBom by value(default = true)
}

class LombokSettings: SchemaNode() {
    
    @Shorthand
    @SchemaDoc("Enables Lombok")
    val enabled by value(default = false)

    @SchemaDoc("The version of Lombok to use for the runtime library and the annotation processor")
    val version by value(default = DefaultVersions.lombok)
}

class InternalSettings : SchemaNode() {
    /**
     * A temporary internal solution that we have for `exclude` in DR until we have a properly designed support.
     * a list of "<group>:<artifact>" strings.
     * Each dependency in the module that matches any such entry is excluded from DR completely.
     * */
    val excludeDependencies: List<String> by value(default = emptyList())
}