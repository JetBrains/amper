package org.jetbrains.deft.proto.core

import java.nio.file.Path
import kotlin.io.path.name

private const val TEMPLATE_SUFFIX = ".Pot-template.yaml"

val Path.templateName: String
    get() = name.substringBeforeLast(TEMPLATE_SUFFIX)
