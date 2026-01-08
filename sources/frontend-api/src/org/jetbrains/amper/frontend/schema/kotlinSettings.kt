/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.CanBeReferenced
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.EnumOrderSensitive
import org.jetbrains.amper.frontend.api.EnumValueFilter
import org.jetbrains.amper.frontend.api.Misnomers
import org.jetbrains.amper.frontend.api.PlatformAgnostic
import org.jetbrains.amper.frontend.api.PlatformSpecific
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Shorthand
import org.jetbrains.amper.frontend.api.StandaloneSpecific
import org.jetbrains.amper.frontend.api.TraceableString

/**
 * The expected pattern for the Kotlin compiler version setting.
 * It's used in diagnostics and to extract the default language version from the compiler version string.
 */
val KotlinCompilerVersionPattern = Regex("""(?<languageVersion>\d+\.\d+)\..*""")

@EnumOrderSensitive(reverse = true)
@EnumValueFilter("outdated", isNegated = true)
enum class KotlinVersion(override val schemaValue: String, override val outdated: Boolean = false) : SchemaEnum {
    Kotlin10("1.0", outdated = true),
    Kotlin11("1.1", outdated = true),
    Kotlin12("1.2", outdated = true),
    Kotlin13("1.3", outdated = true),
    Kotlin14("1.4", outdated = true),
    Kotlin15("1.5", outdated = true),
    Kotlin16("1.6", outdated = true),
    Kotlin17("1.7", outdated = true),
    Kotlin18("1.8", outdated = true), // deprecated in 2.2
    Kotlin19("1.9", outdated = true), // deprecated in 2.2
    Kotlin20("2.0"),
    Kotlin21("2.1"),
    Kotlin22("2.2"),
    Kotlin23("2.3"), // experimental in 2.2
    ;

    override fun toString(): String = schemaValue
    companion object Index : EnumMap<KotlinVersion, String>(KotlinVersion::values, KotlinVersion::schemaValue)
}

@SchemaDoc("Preset options for the all-open compiler plugin")
enum class AllOpenPreset(override val schemaValue: String, override val outdated: Boolean = false) : SchemaEnum {
    @SchemaDoc("Automatically adds annotations used by the Spring framework")
    Spring("spring"),
    
    @SchemaDoc("Automatically adds annotations used by the Micronaut framework")
    Micronaut("micronaut"),
    
    @SchemaDoc("Automatically adds annotations used by the Quarkus framework")
    Quarkus("quarkus");
    
    companion object Index : EnumMap<AllOpenPreset, String>(AllOpenPreset::values, AllOpenPreset::schemaValue)
}

@SchemaDoc("Preset options for the no-arg compiler plugin")
enum class NoArgPreset(override val schemaValue: String, override val outdated: Boolean = false) : SchemaEnum {
    @SchemaDoc("Automatically adds no-arg constructors to JPA entity classes")
    Jpa("jpa");
    
    companion object Index : EnumMap<NoArgPreset, String>(NoArgPreset::values, NoArgPreset::schemaValue)
}

class NoArgSettings : SchemaNode() {
    @Shorthand
    @SchemaDoc("Enables the Kotlin no-arg compiler plugin")
    val enabled by value(false)

    @SchemaDoc("List of annotations that trigger no-arg constructor generation. Classes annotated with these annotations will have a no-arg constructor generated.")
    val annotations by nullableValue<List<TraceableString>>()

    @SchemaDoc("Whether to call initializers in the synthesized constructor. By default, initializers are not called.")
    val invokeInitializers by value(false)
    
    @SchemaDoc("Predefined sets of annotations. Currently only 'jpa' preset is supported, which automatically includes JPA entity annotations.")
    val presets by nullableValue<List<NoArgPreset>>()
}

class AllOpenSettings : SchemaNode() {
    @Shorthand
    @SchemaDoc("Enables the Kotlin all-open compiler plugin")
    val enabled by value(false)

    @SchemaDoc("List of annotations that trigger open class/method generation. Classes/methods annotated with these annotations will be automatically made open.")
    val annotations by nullableValue<List<TraceableString>>()
    
    @SchemaDoc("Predefined sets of annotations for common frameworks. Each preset automatically includes annotations specific to that framework.")
    val presets by nullableValue<List<AllOpenPreset>>()
}

class JsPlainObjectsSettings : SchemaNode() {
    @Shorthand
    @SchemaDoc("Enables the Kotlin JS plain objects compiler plugin")
    val enabled by value(false)
}

class PowerAssertSettings : SchemaNode() {
    @Shorthand
    @SchemaDoc("Enables the Kotlin power-assert compiler plugin")
    val enabled by value(false)

    @SchemaDoc("A list of fully-qualified function names that the Power-assert plugin should transform. " +
            "If not specified, only kotlin.assert() calls will be transformed by default.")
    val functions by value(listOf(TraceableString("kotlin.assert", DefaultTrace)))
}

class KotlinSettings : SchemaNode() {

    @PlatformAgnostic
    @Misnomers("compiler")
    @SchemaDoc("The version of the Kotlin compiler and standard library to use")
    val version by value(DefaultVersions.kotlin)

    @CanBeReferenced  // by apiVersion
    @PlatformAgnostic
    @Misnomers("language-version")
    @SchemaDoc("Source compatibility with the specified version of Kotlin")
    val languageVersion by nullableValue<KotlinVersion>()

    @PlatformAgnostic
    @Misnomers("api-version", "sdkVersion", "sdk")
    @SchemaDoc("Allow using declarations only from the specified version of Kotlin bundled libraries")
    val apiVersion by dependentValue(::languageVersion)

    @Misnomers("Werror")
    @SchemaDoc("Turn any warnings into a compilation error")
    val allWarningsAsErrors by value(false)

    @Misnomers("compilation", "arguments", "options")
    @SchemaDoc("Pass any [compiler option](https://kotlinlang.org/docs/compiler-reference.html#compiler-options) directly")
    val freeCompilerArgs by nullableValue<List<TraceableString>>()

    @Misnomers("nowarn")
    @SchemaDoc("Suppress the compiler from displaying warnings during compilation")
    val suppressWarnings by value(false)

    @SchemaDoc("Enables verbose logging output which includes details of the compilation process")
    val verbose by value(false)

    @SchemaDoc("(Only for [native targets](https://kotlinlang.org/docs/native-target-support.html)) " +
            "Additional arguments to pass to the linker during binary building.")
    @PlatformSpecific(Platform.NATIVE)
    @Misnomers("linkerOpts", "arguments")
    val linkerOptions by nullableValue<List<TraceableString>>()

    @SchemaDoc("(Only for [native targets](https://kotlinlang.org/docs/native-target-support.html)) " +
            "Enables emitting debug information. Enabled in debug variants by default.")
    @PlatformSpecific(Platform.NATIVE)
    val debug by nullableValue<Boolean>()

    @SchemaDoc("(Only for [native targets](https://kotlinlang.org/docs/native-target-support.html)) " +
            "Enables compilation optimizations and produce a binary with better runtime performance. " +
            "Enabled in release variants by default.")
    @PlatformSpecific(Platform.NATIVE)
    val optimization by nullableValue<Boolean>()

    @SchemaDoc("Enables the [progressive mode for the compiler](https://kotlinlang.org/docs/compiler-reference.html#progressive)")
    val progressiveMode by value(false)

    // TODO Add doc
    // @SchemaDoc("")
    val languageFeatures by nullableValue<List<TraceableString>>()
    
    @SchemaDoc("Usages of API that [requires opt-in](https://kotlinlang.org/docs/opt-in-requirements.html) with a requirement annotation with the given fully qualified name")
    val optIns by nullableValue<List<TraceableString>>()

    @StandaloneSpecific
    @SchemaDoc("[KSP (Kotlin Symbol Processing)](https://github.com/google/ksp) settings.")
    val ksp: KspSettings by nested()

    @PlatformAgnostic
    @SchemaDoc("Configure [Kotlin serialization](https://github.com/Kotlin/kotlinx.serialization)")
    val serialization: SerializationSettings by nested()
    
    @PlatformAgnostic
    @SchemaDoc("Configure the [Kotlin no-arg compiler plugin](https://kotlinlang.org/docs/no-arg-plugin.html)")
    val noArg: NoArgSettings by nested()
    
    @PlatformAgnostic
    @SchemaDoc("Configure the [Kotlin all-open compiler plugin](https://kotlinlang.org/docs/all-open-plugin.html)")
    val allOpen: AllOpenSettings by nested()

    @PlatformSpecific(Platform.JS)
    @SchemaDoc("Configure the [Kotlin JS plain objects compiler plugin](https://kotlinlang.org/docs/js-plain-objects.html)")
    val jsPlainObjects: JsPlainObjectsSettings by nested()

    @PlatformAgnostic
    @SchemaDoc("Configure the [Kotlin power-assert compiler plugin](https://kotlinlang.org/docs/power-assert.html)")
    val powerAssert: PowerAssertSettings by nested()
}
