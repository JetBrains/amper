/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core.system

import org.jetbrains.amper.system.info.OsFamily
import org.jetbrains.amper.system.info.SystemInfo
import kotlin.DeprecationLevel

@Deprecated(
    message = "Moved to org.jetbrains.amper.system.info",
    replaceWith = ReplaceWith("OsFamily", imports = ["org.jetbrains.amper.system.info.OsFamily"]),
    level = DeprecationLevel.ERROR,
)
typealias OSFamily = OsFamily

@Deprecated(
    message = "Replaced by org.jetbrains.amper.system.info.SystemInfo.CurrentHost",
    replaceWith = ReplaceWith("SystemInfo.CurrentHost", imports = ["org.jetbrains.amper.system.info.SystemInfo"]),
    level = DeprecationLevel.ERROR,
)
typealias DefaultSystemInfo = SystemInfo.CurrentHost
