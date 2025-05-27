/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core

import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolute


fun Path.resolveAbsoluteSafely(part: String): Path? = runCatching {
    resolve(part.replace("/", File.separator)).absolute().normalize()
}.getOrNull()