/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package pkg

import kotlin.test.Test
import kotlin.test.assertEquals

class EnvVarsTest {
    @Test
    fun envFromSettingsIsVisible() {
        assertEquals(
            expected = "hello-from-settings",
            actual = System.getenv("MY_TEST_ENV_FROM_SETTINGS"),
            message = "Environment variable from settings.jvm.test.environmentVariables should be visible to JVM tests",
        )
    }
}
