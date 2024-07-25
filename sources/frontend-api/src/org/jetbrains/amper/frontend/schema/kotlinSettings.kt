/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.EnumOrderSensitive
import org.jetbrains.amper.frontend.api.PlatformSpecific
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode

@EnumOrderSensitive(reverse = true)
enum class KotlinVersion(override val schemaValue: String, override val outdated: Boolean = false) : SchemaEnum {
    Kotlin10("1.0"),
    Kotlin11("1.1"),
    Kotlin12("1.2"),
    Kotlin13("1.3"),
    Kotlin14("1.4"),
    Kotlin15("1.5"),
    Kotlin16("1.6"),
    Kotlin17("1.7"),
    Kotlin18("1.8"),
    Kotlin19("1.9"),
    Kotlin20("2.0"),
    ;

    override fun toString(): String = schemaValue
    companion object Index : EnumMap<KotlinVersion, String>(KotlinVersion::values, KotlinVersion::schemaValue)
}

class KotlinSettings : SchemaNode() {
    @SchemaDoc("Configure the [Kotlin serialization](https://github.com/Kotlin/kotlinx.serialization)")
    var serialization by nullableValue<SerializationSettings>()

    @SchemaDoc("Source compatibility with the specified version of Kotlin")
    var languageVersion by value(KotlinVersion.Kotlin20)

    @SchemaDoc("Allow using declarations only from the specified version of Kotlin bundled libraries")
    var apiVersion by value { languageVersion }

    @SchemaDoc("Turn any warnings into a compilation error")
    var allWarningsAsErrors by value(false)

    @SchemaDoc("Pass any [compiler option](https://kotlinlang.org/docs/compiler-reference.html#compiler-options) directly")
    var freeCompilerArgs by nullableValue<List<String>>()

    @SchemaDoc("Suppress the compiler from displaying warnings during compilation")
    var suppressWarnings by value(false)

    @SchemaDoc("Enable verbose logging output which includes details of the compilation process")
    var verbose by value(false)

//    @SchemaDoc("")
    var linkerOpts by nullableValue<List<String>>()

    @SchemaDoc("(Only for [native targets](https://kotlinlang.org/docs/native-target-support.html)) Enable emitting debug information")
    @PlatformSpecific(Platform.NATIVE)
    var debug by value(true)

    @SchemaDoc("Enable the [progressive mode for the compiler](https://kotlinlang.org/docs/compiler-reference.html#progressive)")
    var progressiveMode by value(false)

    // TODO Replace with enum
//    @SchemaDoc("")
    var languageFeatures by nullableValue<List<String>>()

    // TODO Replace with enum
    @SchemaDoc("Usages of API that [requires opt-in](https://kotlinlang.org/docs/opt-in-requirements.html) with a requirement annotation with the given fully qualified name")
    var optIns by nullableValue<List<String>>()
}