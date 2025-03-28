/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core

import com.sun.jna.platform.win32.KnownFolders
import com.sun.jna.platform.win32.Shell32Util
import org.jetbrains.amper.core.system.OsFamily
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.pathString

sealed interface AmperUserCacheInitializationResult

sealed interface AmperUserCacheInitializationFailure : AmperUserCacheInitializationResult {
    val defaultMessage: String

    class InvalidPath(val rawBasePath: String, val source: String) : AmperUserCacheInitializationFailure {
        override val defaultMessage: String
            get() = "Amper cache path failed to resolve because $source contained an invalid path: \"$rawBasePath\". Configure it as a valid absolute path instead."
    }

    class NonAbsolutePath(val path: Path, val source: String) : AmperUserCacheInitializationFailure {
        override val defaultMessage: String
            get() = "Amper cache path is set via the $source to a non-absolute path: \"${path.pathString}\". " +
                    "This could affect where the cache is located based on the current directory Amper is run from. Configure it as an absolute path instead."
    }
}

data class AmperUserCacheRoot(val path: Path) : AmperUserCacheInitializationResult {
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
        private const val AMPER_CACHE_SUBFOLDER = "Amper"

        /**
         * The error here is user-friendly and can be reported as is.
         */
        fun fromCurrentUserResult(): AmperUserCacheInitializationResult =
            getFromSystemSettings()
                ?: getFromEnvSettings()
                ?: when (OsFamily.current) {
                    OsFamily.Windows -> getWindowsCacheFolder()
                    OsFamily.MacOs -> getMacOsCacheFolder()
                    OsFamily.Linux,
                    OsFamily.FreeBSD,
                    OsFamily.Solaris -> getGenericUnixCacheFolder()
                }

        private fun getFromSystemSettings(): AmperUserCacheInitializationResult? {
            val systemPropertyPath = System.getProperty("amper.cache.root")?.takeIf { it.isNotBlank() } ?: return null
            return systemPropertyPath.buildAmperCacheRoot(source = "\"amper.cache.root\" system property")
        }

        private fun getFromEnvSettings(): AmperUserCacheInitializationResult? {
            val envVariablePath = System.getenv("AMPER_CACHE_ROOT")?.takeIf { it.isNotBlank() } ?: return null
            return envVariablePath.buildAmperCacheRoot(source = "\"AMPER_CACHE_ROOT\" environment variable")
        }

        private fun getWindowsCacheFolder(): AmperUserCacheInitializationResult {
            // We prefer the env variable because getting known folders through Shell32Util is slow (~300-400ms)
            val localAppDataFromEnv = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
            if (localAppDataFromEnv != null) {
                val localAppDataFolder =
                    localAppDataFromEnv.buildAmperCacheRoot(source = "\"LOCALAPPDATA\" environment variable")
                /**
                 * If the LOCALAPPDATA environment variable is corrupted in some way, we'll try retrieving
                 * the cache folder via the Known Folders system.
                 */
                if (localAppDataFolder !is AmperUserCacheInitializationFailure) {
                    return localAppDataFolder
                }
            }

            return Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_LocalAppData)
                .buildAmperCacheRoot(source = "Windows Known Folders system (FOLDERID_LocalAppData property)") { this / AMPER_CACHE_SUBFOLDER }
        }

        private fun getMacOsCacheFolder(): AmperUserCacheInitializationResult {
            return System.getProperty("user.home")
                .buildAmperCacheRoot(source = "user home system property") { this / "Library" / "Caches" / AMPER_CACHE_SUBFOLDER }
        }

        private fun getGenericUnixCacheFolder(): AmperUserCacheInitializationResult {
            val xdgCacheHome = System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() }
            if (xdgCacheHome != null) {
                val xdgCacheFolder =
                    xdgCacheHome.buildAmperCacheRoot(source = "\"XDG_CACHE_HOME\" environment variable") { this / AMPER_CACHE_SUBFOLDER }
                /**
                 * If XDG_CACHE_HOME wasn't pointing to a proper path, the XDG specification suggests ignoring it:
                 * > If an implementation encounters a relative path in any of these variables
                 * > it should consider the path invalid and ignore it.
                 *
                 * https://specifications.freedesktop.org/basedir-spec/0.8/
                 *
                 * Thus, we'll continue with the user.home based path.
                 */
                if (xdgCacheFolder !is AmperUserCacheInitializationFailure) {
                    return xdgCacheFolder
                }
            }

            return System.getProperty("user.home")
                .buildAmperCacheRoot(source = "user home system property") { this / ".cache" / AMPER_CACHE_SUBFOLDER }
        }

        private inline fun String.buildAmperCacheRoot(
            source: String,
            buildPathFromSource: Path.() -> Path = { this }
        ): AmperUserCacheInitializationResult {
            val path = try {
                Path(this)
            } catch (_: InvalidPathException) {
                return AmperUserCacheInitializationFailure.InvalidPath(this, source)
            }
            val finalPath = path.buildPathFromSource()
            return if (finalPath.isAbsolute) {
                AmperUserCacheRoot(finalPath)
            } else {
                AmperUserCacheInitializationFailure.NonAbsolutePath(finalPath, source)
            }
        }
    }
}
