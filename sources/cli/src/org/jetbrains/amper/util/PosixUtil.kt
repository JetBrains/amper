/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.util

import java.nio.file.FileSystems
import java.nio.file.attribute.PosixFilePermission

object PosixUtil {
    val isPosixFileSystem = FileSystems.getDefault().supportedFileAttributeViews().contains("posix")

    private const val S_IRUSR = 256
    private const val S_IWUSR = 128
    private const val S_IXUSR = 64
    private const val S_IRGRP = 32
    private const val S_IWGRP = 16
    private const val S_IXGRP = 8
    private const val S_IROTH = 4
    private const val S_IWOTH = 2
    private const val S_IXOTH = 1

    fun toUnixMode(permissions: Set<PosixFilePermission>): Int {
        var mode = 0
        for (permission in permissions) {
            mode = when (permission) {
                PosixFilePermission.OWNER_READ -> mode or S_IRUSR
                PosixFilePermission.OWNER_WRITE -> mode or S_IWUSR
                PosixFilePermission.OWNER_EXECUTE -> mode or S_IXUSR
                PosixFilePermission.GROUP_READ -> mode or S_IRGRP
                PosixFilePermission.GROUP_WRITE -> mode or S_IWGRP
                PosixFilePermission.GROUP_EXECUTE -> mode or S_IXGRP
                PosixFilePermission.OTHERS_READ -> mode or S_IROTH
                PosixFilePermission.OTHERS_WRITE -> mode or S_IWOTH
                PosixFilePermission.OTHERS_EXECUTE -> mode or S_IXOTH
            }
        }
        return mode
    }
}
