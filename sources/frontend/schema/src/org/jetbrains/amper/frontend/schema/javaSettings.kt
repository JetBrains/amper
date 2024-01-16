/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.SchemaNode


enum class JavaVersion(override val schemaValue: String, override val outdated: Boolean = false) : SchemaEnum {
    VERSION_1_1("1.1", outdated = true),
    VERSION_1_2("1.2", outdated = true),
    VERSION_1_3("1.3", outdated = true),
    VERSION_1_4("1.4", outdated = true),
    VERSION_1_5("1.5", outdated = true),
    VERSION_1_6("1.6", outdated = true),
    VERSION_1_7("1.7", outdated = true),
    VERSION_1_8("1.8"),
    VERSION_1_9("1.9", outdated = true),
    VERSION_1_10("1.10", outdated = true),
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
    VERSION_25("25"),;
    companion object Index : EnumMap<JavaVersion, String>(JavaVersion::values, JavaVersion::schemaValue)
}

class JavaSettings : SchemaNode() {
    var source by nullableValue<JavaVersion>()
}

class JvmSettings : SchemaNode() {
    var target by value<JavaVersion>().default(JavaVersion.VERSION_17)
    var mainClass by nullableValue<String>()
}