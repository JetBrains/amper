/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core

import com.sun.jna.platform.win32.KnownFolders
import com.sun.jna.platform.win32.Shell32Util
import org.jetbrains.amper.core.system.OsFamily
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.isDirectory


data class AmperUserCacheRoot(val path: Path) {
    init {
        require(path.isDirectory()) {
            "User cache root is not a directory: $path"
        }
        require(path.isAbsolute) {
            "User cache root is not an absolute path: $path"
        }
    }

    val downloadCache by lazy {
        path.resolve("download.cache").also { it.createDirectories() }
    }

    val extractCache by lazy {
        path.resolve("extract.cache").also { it.createDirectories() }
    }

    companion object {
        fun fromCurrentUser(): AmperUserCacheRoot {
            val userHome = Path.of(System.getProperty("user.home"))

            val localAppData = when (OsFamily.current) {
                OsFamily.Windows -> Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_LocalAppData)?.let { Path.of(it) } ?: (userHome / "AppData/Local")
                OsFamily.MacOs -> userHome / "Library/Caches"
                OsFamily.Linux, OsFamily.FreeBSD, OsFamily.Solaris -> {
                    val xdgCacheHome = System.getenv("XDG_CACHE_HOME")
                    if (xdgCacheHome.isNullOrBlank()) userHome.resolve(".cache") else Path.of(xdgCacheHome)
                }
            }

            val localAppDataAmper = localAppData.resolve("Amper")

            localAppDataAmper.createDirectories()

            return AmperUserCacheRoot(localAppDataAmper)
        }
    }
}