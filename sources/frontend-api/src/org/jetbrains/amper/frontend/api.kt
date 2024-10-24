/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import java.nio.file.Path


interface Model {
    val projectRoot: Path
    val modules: List<AmperModule>
}
