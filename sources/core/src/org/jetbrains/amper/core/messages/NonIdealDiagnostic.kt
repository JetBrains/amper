/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core.messages

@RequiresOptIn(
    message = "This reports a diagnostic that might not be very friendly to users. Make sure you understand the UX it " +
            "provides by reading the KDoc, and check if there are no better alternatives.",
)
annotation class NonIdealDiagnostic