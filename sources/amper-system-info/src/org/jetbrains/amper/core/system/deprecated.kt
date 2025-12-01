/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core.system

import org.jetbrains.amper.system.info.DefaultSystemInfo
import org.jetbrains.amper.system.info.OsFamily

@Deprecated(
    message = "Moved to org.jetbrains.amper.system.info",
    replaceWith = ReplaceWith("OsFamily", imports = ["org.jetbrains.amper.system.info.OsFamily"])
)
typealias OSFamily = OsFamily

@Deprecated(
    message = "Moved to org.jetbrains.amper.system.info",
    replaceWith = ReplaceWith("DefaultSystemInfo", imports = ["org.jetbrains.amper.system.info.DefaultSystemInfo"])
)
typealias DefaultSystemInfo = DefaultSystemInfo
