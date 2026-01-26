/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins.detekt

import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.Configurable
import org.jetbrains.amper.plugins.ModuleSources

@Configurable
interface CommonRunDetectSettings {
    val settings: Settings
    val sources: ModuleSources
    val moduleClasspath: Classpath
    val detektClasspath: Classpath
}