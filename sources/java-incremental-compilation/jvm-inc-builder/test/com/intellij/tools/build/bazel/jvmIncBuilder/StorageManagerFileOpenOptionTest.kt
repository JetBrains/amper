/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package com.intellij.tools.build.bazel.jvmIncBuilder

import com.intellij.tools.build.bazel.amper.StorageManagerFileOpenOption
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class StorageManagerFileOpenOptionTest {
  /**
   * This test checks that a file opening option is correctly initialized to the
   * [com.sun.nio.file.ExtendedOpenOption.NOSHARE_WRITE].
   * Initialization is done with the help of reflection to avoid compilation warnings.
   */
  @Test
  fun testStorageManagerOption() {
    assertEquals(StorageManagerFileOpenOption.writeOption::class.simpleName, "ExtendedOpenOption")
    assertEquals(StorageManagerFileOpenOption.writeOption.toString(), "NOSHARE_WRITE")
  }
}