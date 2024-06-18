/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.catalogs

import com.intellij.openapi.vfs.VirtualFile

interface VersionsCatalogProvider {

    /**
     * Try to find catalog path for given module or template path.
     */
    fun getCatalogPathFor(file: VirtualFile): VirtualFile?
}
