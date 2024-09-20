/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core

import com.sun.jna.platform.win32.KnownFolders
import com.sun.jna.platform.win32.Shell32Util
import org.jetbrains.amper.core.system.OsFamily
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.pathString

data class AmperUserCacheRoot(val path: Path) {
    init {
        require(path.isAbsolute) {
            "User cache root is not an absolute path: $path"
        }
        path.createDirectories() // fails if it exists but is a file
    }

    val downloadCache by lazy { path.resolve("download.cache").createDirectories() }

    val extractCache by lazy { path.resolve("extract.cache").createDirectories() }

    // we want the CLI help to look clean
    override fun toString(): String = path.pathString

    companion object {
        fun fromCurrentUser(): AmperUserCacheRoot {
            val userHome = Path(System.getProperty("user.home"))

            val localAppData = when (OsFamily.current) {
                OsFamily.Windows -> Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_LocalAppData)?.let { Path(it) }
                    ?: (userHome / "AppData/Local")
                OsFamily.MacOs -> userHome / "Library/Caches"
                OsFamily.Linux,
                OsFamily.FreeBSD,
                OsFamily.Solaris -> System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() }?.let { Path(it) }
                    ?: (userHome / ".cache")
            }

            return AmperUserCacheRoot(localAppData.resolve("Amper"))
        }
    }
}
