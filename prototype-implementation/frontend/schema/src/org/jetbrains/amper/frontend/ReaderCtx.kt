/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import java.io.Reader
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.reader

data class ReaderCtx(
    val path2Reader: (Path) -> Reader? = { if (it.exists()) it.reader() else null },
)