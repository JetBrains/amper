/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("PropertyName")

package org.jetbrains.amper.dependency.resolution.metadata.json

import kotlinx.serialization.json.Json

internal val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

