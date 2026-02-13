/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package com.example;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SurefireTest {

    @Test
    void testEnvironmentVariables() {
        assertEquals("test-value", System.getenv("MY_TEST_ENV"), "MY_TEST_ENV should be set");
        assertEquals("another-value", System.getenv("ANOTHER_ENV"), "ANOTHER_ENV should be set");
    }

    @Test
    void testSystemProperties() {
        assertEquals("prop-value", System.getProperty("my.test.prop"), "my.test.prop should be set");
        assertEquals("another-prop-value", System.getProperty("another.prop"), "another.prop should be set");
    }
}
