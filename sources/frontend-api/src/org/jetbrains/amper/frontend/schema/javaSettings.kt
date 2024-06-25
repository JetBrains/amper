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

@EnumOrderSensitive(reverse = true)
enum class JavaVersion(
    override val schemaValue: String,
    override val outdated: Boolean = false,
    val legacyNotation: String = schemaValue,
) : SchemaEnum {
    // TODO remove entries below Java 8, as they are not supported by the Kotlin compiler options that we set from it
    //   Do we need them anyway?
    VERSION_1("1", outdated = true, legacyNotation = "1.1"),
    VERSION_2("2", outdated = true, legacyNotation = "1.2"),
    VERSION_3("3", outdated = true, legacyNotation = "1.3"),
    VERSION_4("4", outdated = true, legacyNotation = "1.4"),
    VERSION_5("5", outdated = true, legacyNotation = "1.5"),
    VERSION_6("6", outdated = true, legacyNotation = "1.6"),
    VERSION_7("7", outdated = true, legacyNotation = "1.7"),
    VERSION_8("8", legacyNotation = "1.8"),
    VERSION_9("9", outdated = true),
    VERSION_10("10", outdated = true),
    VERSION_11("11"),
    VERSION_12("12", outdated = true),
    VERSION_13("13", outdated = true),
    VERSION_14("14", outdated = true),
    VERSION_15("15", outdated = true),
    VERSION_16("16", outdated = true),
    VERSION_17("17"),
    VERSION_18("18", outdated = true),
    VERSION_19("19", outdated = true),
    VERSION_20("20", outdated = true),
    VERSION_21("21"),
    VERSION_22("22"),
    VERSION_23("23"),
    VERSION_24("24"),
    VERSION_25("25");

    /**
     * The integer representation of this version for the `--release` option of the Java compiler, and the
     * `-Xjdk-release` option of the Kotlin compiler.
     *
     * Notes:
     *  * The Java compiler only supports versions from 6 and above for the `--release` option.
     *  * Despite the documentation, the Kotlin compiler supports "8" as an alias for "1.8" in `-Xjdk-release`, but the
     *    `-jvm-target` option requires "1.8".
     */
    val releaseNumber: Int = schemaValue.toInt()

    companion object Index : EnumMap<JavaVersion, String>(JavaVersion::values, JavaVersion::schemaValue)
}

class JvmSettings : SchemaNode() {

    @SchemaDoc("The minimum JVM release version that the code should be compatible with. " +
            "This enforces compatibility on 3 levels. " +
            "First, it is used as the target version for the bytecode generated from Kotlin and Java sources. " +
            "Second, it limits the Java platform APIs available to Kotlin and Java sources. " +
            "Third, it limits the Java language constructs in Java sources. " +
            "If this is set to null, these constraints are not applied and the compiler defaults are used.")
    var release by nullableValue<JavaVersion>(JavaVersion.VERSION_17) // TODO discuss the default

    @SchemaDoc("(Only for `jvm/app` [product type](#product-types)). The fully-qualified name of the class used to run the application")
    @ProductTypeSpecific(ProductType.JVM_APP)
    var mainClass by nullableValue<String>()
}
