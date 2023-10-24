package org.jetbrains.amper.core

import java.nio.file.Path
import kotlin.io.path.name

private const val TEMPLATE_SUFFIX = ".module-template.yaml"

val Path.templateName: String
    get() = name.substringBeforeLast(TEMPLATE_SUFFIX)
