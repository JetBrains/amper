/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test.assertions

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.trace.data.SpanData
import kotlin.test.assertEquals
import kotlin.test.fail

fun <T> SpanData.assertHasAttribute(key: AttributeKey<T>, value: T) {
    val actualValue = attributes[key]
        ?: fail("Attribute '$key' is missing in span '$name'")
    assertEquals(value, actualValue, "Wrong value for attribute '$key' in span $name: expected '$value' but was '$actualValue'")
}
