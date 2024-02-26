/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core

import java.nio.file.Path
import kotlin.io.path.name

private const val TEMPLATE_SUFFIX = ".module-template.yaml"

val Path.templateName: String
    get() = name.substringBeforeLast(TEMPLATE_SUFFIX)
