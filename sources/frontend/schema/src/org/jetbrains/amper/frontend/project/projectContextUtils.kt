/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.project

import com.intellij.openapi.vfs.isFile

val AmperProjectContext.wrapperInstalled: Boolean
    get() = projectRootDir.children.filter { it.isFile }.filter { it.name in setOf("amper", "amper.bat") }.size == 2