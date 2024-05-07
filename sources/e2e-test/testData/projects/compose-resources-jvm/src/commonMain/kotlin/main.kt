/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import compose_resources_jvm.generated.resources.Res
import compose_resources_jvm.generated.resources.hello
import org.jetbrains.compose.resources.ExperimentalResourceApi


@OptIn(ExperimentalResourceApi::class)
fun main(args: Array<String>) {
    println(Res.string.hello.key)
}