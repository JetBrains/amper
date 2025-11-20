/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package com.intellij.tools.build.bazel.amper

import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption

/**
 * Direct usage of [com.sun.nio.file.ExtendedOpenOption.NOSHARE_WRITE] leads to compilation warning.
 * To suppress the warning, reflection is used to get enum value.
 * @see com.intellij.tools.build.bazel.jvmIncBuilder.StorageManagerFileOpenOptionTest
 */
object StorageManagerFileOpenOption {
  val writeOption: OpenOption = try {
    Class.forName("com.sun.nio.file.ExtendedOpenOption")
      .getEnumConstants()
      .firstOrNull { it.toString() == "NOSHARE_WRITE" }
      ?.let { it as OpenOption }
      ?: StandardOpenOption.WRITE
  } catch (_: ClassNotFoundException) {
    StandardOpenOption.WRITE
  }
}