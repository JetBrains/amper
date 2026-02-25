/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins.settings.reference

import org.jetbrains.amper.plugins.*

@Configurable
interface Publication {
    val group: String
    val name: String
    val version: String
}

@TaskAction
fun checkSettings(
    likeSettings: String,
    likePublication: Publication,
    listString: List<String>,
) = Unit
