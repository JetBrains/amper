/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.downloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.core.extract.ExtractOptions
import org.jetbrains.amper.core.extract.extractFileWithFlag
import org.jetbrains.amper.core.extract.getExtractOptionsShortString
import java.nio.file.Path
import kotlin.io.path.createDirectories


suspend fun extractFileToCacheLocation(
    archiveFile: Path,
    amperUserCacheRoot: AmperUserCacheRoot,
    vararg options: ExtractOptions
): Path = withContext(Dispatchers.IO) {
    val cachePath = amperUserCacheRoot.path.resolve("extract.cache")
        .also { it.createDirectories() }
    val hash = Downloader.hashString(archiveFile.toString() + getExtractOptionsShortString(options)).substring(0, 6)
    val directoryName = "${archiveFile.fileName}.${hash}.d"
    val targetDirectory = cachePath.resolve(directoryName)
    val flagFile = cachePath.resolve("${directoryName}.flag")
    extractFileWithFlag(archiveFile, targetDirectory, flagFile, *options)
}