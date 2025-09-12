/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package com.example

import org.jetbrains.amper.*
import java.nio.file.Path

@Schema interface Settings {
    val boolean: Boolean get() = false
    val requiredBoolean: Boolean
    val requiredString: String
    val nullablePath: Path? get() = null
    val path: Path
    val int: Int
    val nested: Nested
}

@Schema interface Nested {
    val string: String get() = "string"
    val list: List<Element> get() = emptyList()
    val map: Map<String, Element> get() = emptyMap()
}

@Schema interface Element {
    val enum: SomeEnum
    val string: String
}

enum class SomeEnum {
    one, two, three,
}

@TaskAction
fun someAction(
    boolean1: Boolean,
    boolean2: Boolean,
    requiredString: String,
    @Input nullablePath: Path?,
    @Input path: Path,
    int: Int,
    nullableInt: Int?,
    nested1: Nested,
    nested2: Nested,
) {}