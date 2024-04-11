/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import generated.compose.resources.Res
import generated.compose.resources.hello
import org.jetbrains.compose.resources.ExperimentalResourceApi


@OptIn(ExperimentalResourceApi::class)
fun accessJvm() {
    Res.string.hello.key
}