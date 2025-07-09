/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.project

import java.nio.file.Path
import kotlin.io.path.div

val AmperProjectContext.projectLocalCacheDirectory: Path
    get() = projectRootDir.toNioPath() / AMPER_PROJECT_LOCAL_CACHE_DIR_NAME

val AmperProjectContext.pluginInternalSchemaDirectory: Path
    get() = projectLocalCacheDirectory / "plugins"

private const val AMPER_PROJECT_LOCAL_CACHE_DIR_NAME = ".amper"
