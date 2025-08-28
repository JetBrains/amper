/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.stdlib.regex

import kotlin.reflect.KProperty

/**
 * Allows delegating properties to named capture groups.
 */
operator fun MatchResult.getValue(thisRef: Any?, property: KProperty<*>): String? {
    return groups[property.name]?.value
}