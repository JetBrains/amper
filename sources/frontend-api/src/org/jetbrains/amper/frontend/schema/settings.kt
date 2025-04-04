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
import org.jetbrains.amper.frontend.api.ContextAgnostic
import org.jetbrains.amper.frontend.api.PlatformSpecific
import org.jetbrains.amper.frontend.api.ProductTypeSpecific
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Shorthand
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

    @ContextAgnostic
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

    @Aliases("coverage")
    @SchemaDoc("Kover settings for code coverage. Read more [about Kover](https://kotlin.github.io/kotlinx-kover/gradle-plugin/)")
    var kover by nullableValue<KoverSettings>()

    @SchemaDoc("Native applications settings")
    @PlatformSpecific(Platform.NATIVE)
    var native by nullableValue<NativeSettings>()

    @PlatformSpecific(Platform.JVM)
    @SchemaDoc("Ktor server settings")
    var ktor by value(::KtorServerSettings)

    @PlatformSpecific(Platform.JVM)
    @SchemaDoc("Spring Boot settings")
    var springBoot by value(::SpringBootSettings)
}

class ComposeSettings : SchemaNode() {

    @SchemaDoc("Enable Compose runtime, dependencies and the compiler plugins")
    var enabled by value(default = false)

    @SchemaDoc("The Compose plugin version")
    var version by nullableValue<String>("Built-in Compose version") { UsedVersions.composeVersion.takeIf { enabled } }

    @SchemaDoc("Compose Resources settings")
    var resources by value(::ComposeResourcesSettings)
    
    @SchemaDoc("Experimental Compose settings")
    var experimental by value(::ComposeExperimentalSettings)
}

class ComposeResourcesSettings : SchemaNode() {
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
    @SchemaDoc("Enable hot reload")
    var enabled by value(default = false)
}

class SerializationSettings : SchemaNode() {

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

    @GradleSpecific
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

    @Aliases("artifact")
    @SchemaDoc("Artifact ID of the published Maven artifact")
    var name by nullableValue<String>()
}

class KoverSettings : SchemaNode() {

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

class KtorServerSettings: SchemaNode() {
    @SchemaDoc("Enable Ktor server")
    var enabled by value(default = false)

    @SchemaDoc("Ktor version")
    var version by value(default = UsedVersions.ktorVersion)
}

class SpringBootSettings: SchemaNode() {
    @SchemaDoc("Enable Spring Boot")
    var enabled by value(default = false)

    @SchemaDoc("Spring Boot version")
    var version by value(default = UsedVersions.springBootVersion)

    @SchemaDoc("Spring Cloud settings")
    var cloud by dependentValue(::version) {
        SpringCloudSettings(version)
    }

    @SchemaDoc("Spring AI settings")
    var ai by value(::SpringAiSettings)
}


class SpringCloudSettings(private val springBootVersion: String): SchemaNode() {

    val springCloudDefaultVersion: String get() {
        val versionParts = springBootVersion.split(".")
        if (versionParts.size < 2) {
            return "2025.0.0-M1"
        }

        val major = versionParts[0].toIntOrNull() ?: return "2025.0.0-M1"
        val minor = versionParts[1].toIntOrNull() ?: return "2025.0.0-M1"

        // Map of Spring Boot versions to Spring Cloud release trains
        return when {
            // Spring Boot 3.5.x -> Spring Cloud 2025.0.x (Northfields)
            major == 3 && minor == 5 -> "2025.0.0-M1"

            // Spring Boot 3.4.x -> Spring Cloud 2024.0.x (Moorgate)
            major == 3 && minor == 4 -> "2024.0.0"

            // Spring Boot 3.3.x, 3.2.x -> Spring Cloud 2023.0.x (Leyton)
            major == 3 && (minor == 3 || minor == 2) -> "2023.0.4"

            // Spring Boot 3.1.x, 3.0.x -> Spring Cloud 2022.0.x (Kilburn)
            major == 3 && (minor == 1 || minor == 0) -> "2022.0.3"

            // Spring Boot 2.7.x, 2.6.x -> Spring Cloud 2021.0.x (Jubilee)
            major == 2 && (minor == 7 || minor == 6) -> "2021.0.8"

            // Spring Boot 2.5.x, 2.4.x -> Spring Cloud 2020.0.x (Ilford)
            major == 2 && (minor == 5 || minor == 4) -> "2020.0.3"

            // Spring Boot 2.3.x, 2.2.x -> Spring Cloud Hoxton
            major == 2 && (minor == 3 || minor == 2) -> "Hoxton.SR12"

            // Spring Boot 2.1.x -> Spring Cloud Greenwich
            major == 2 && minor == 1 -> "Greenwich.SR6"

            // Spring Boot 2.0.x -> Spring Cloud Finchley
            major == 2 && minor == 0 -> "Finchley.SR4"

            // Spring Boot 1.5.x -> Spring Cloud Edgware/Dalston
            major == 1 && minor == 5 -> "Edgware.SR5"

            // For higher versions, use the latest known version
            major > 3 || (major == 3 && minor > 5) -> "2025.0.0-M1"

            // For other versions, use the latest version
            else -> "2025.0.0-M1"
        }
    }

    @SchemaDoc("Enable Spring Cloud")
    var enabled by value(default = false)

    @SchemaDoc("Spring Cloud version")
    @KnownStringValues(
        "2025.0.0-M1",
        "2024.0.1",
        "2024.0.0",
        "2023.0.5",
        "2023.0.4",
        "2023.0.3",
        "2023.0.2",
        "2023.0.1",
        "2023.0.0",
        "2022.0.5",
        "2022.0.4",
        "2022.0.3",
        "2022.0.2",
        "2022.0.1",
        "2022.0.0",
        "2021.0.9",
        "2021.0.8",
        "2021.0.7",
        "2021.0.6",
        "2021.0.5",
        "2021.0.4",
        "2021.0.3",
        "2021.0.2",
        "2021.0.1",
        "2021.0.0",
        "2020.0.6",
        "2020.0.5",
        "2020.0.4",
        "2020.0.3",
        "2020.0.2",
        "2020.0.1",
        "2020.0.0",
        "Hoxton.SR12",
        "Hoxton.SR11",
        "Hoxton.SR10",
        "Hoxton.SR9",
        "Hoxton.SR8",
        "Hoxton.SR7",
        "Hoxton.SR6",
        "Hoxton.SR5",
        "Hoxton.SR4",
        "Hoxton.SR3",
        "Hoxton.SR2",
        "Hoxton.SR1",
        "Hoxton.RELEASE",
        "Greenwich.SR6",
        "Greenwich.SR5",
        "Greenwich.SR4",
        "Greenwich.SR3",
        "Greenwich.SR2",
        "Greenwich.SR1",
        "Greenwich.RELEASE",
        "Finchley.SR4",
        "Finchley.SR3",
        "Finchley.SR2",
        "Finchley.SR1",
        "Finchley.RELEASE",
        "Edgware.SR6",
        "Edgware.SR5",
        "Edgware.SR4",
        "Edgware.SR3",
        "Edgware.SR2",
        "Edgware.SR1",
        "Edgware.RELEASE",
    )
    var version by value(default = springCloudDefaultVersion)
}


class SpringAiSettings: SchemaNode() {
    @SchemaDoc("Enable Spring AI")
    var enabled by value(default = false)

    @SchemaDoc("Spring AI version")
    var version by value(default = "1.0.0-M6")
}
