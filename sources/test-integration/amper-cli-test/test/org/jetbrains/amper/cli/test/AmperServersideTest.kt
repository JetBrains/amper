/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.junit.jupiter.api.Test

class AmperServersideTest: AmperCliTestBase() {

    @Test
    fun `spring boot library catalog contains spring-modulith-actuator and spring-security-test`() = runSlowTest {
        val result = runCli(testProject("spring-boot-library-catalog"), "show", "dependencies", "--include-tests")
        result.assertStdoutContains("org.springframework.modulith:spring-modulith-actuator")
        result.assertStdoutContains("org.springframework.security:spring-security-test")
    }
}
