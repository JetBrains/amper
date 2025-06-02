/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core

import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.absolute


fun Path.safelyResolveToAbsolute(part: String): Path? = try {
    resolve(part).absolute().normalize()
} catch (_: InvalidPathException) {
    null
}