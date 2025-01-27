/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core.properties

import java.nio.file.Path
import java.util.*
import kotlin.io.path.bufferedReader

/**
 * Reads the contents of this file as a [Properties] instance.
 */
fun Path.readProperties(): Properties = bufferedReader().use { reader ->
    Properties().apply { load(reader) }
}