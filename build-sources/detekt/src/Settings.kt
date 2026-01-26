/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins.detekt

import org.jetbrains.amper.plugins.Configurable
import java.nio.file.Path

@Configurable
interface Settings {
    /**
     * Optional path to a detekt.yml configuration file.
     */
    val configFile: Path?

    /**
     * When using a custom [config file][configFile], the default values are ignored unless you also set this flag.
     */
    val buildUponDefaultConfig: Boolean get() = false
}
