/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core.downloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.extract.ExtractOptions
import org.jetbrains.amper.core.extract.extractFileWithFlag
import org.jetbrains.amper.core.extract.getExtractOptionsShortString
import org.jetbrains.amper.core.hashing.sha256String
import java.nio.file.Path


suspend fun extractFileToCacheLocation(
    archiveFile: Path,
    amperUserCacheRoot: AmperUserCacheRoot,
    vararg options: ExtractOptions
): Path = withContext(Dispatchers.IO) {
    val cachePath = amperUserCacheRoot.extractCache
    val hash = "$archiveFile${getExtractOptionsShortString(options)}".sha256String().take(6)
    val directoryName = "${archiveFile.fileName}.${hash}.d"
    val targetDirectory = cachePath.resolve(directoryName)
    val flagFile = cachePath.resolve("${directoryName}.flag")
    extractFileWithFlag(archiveFile, targetDirectory, flagFile, *options)
}
