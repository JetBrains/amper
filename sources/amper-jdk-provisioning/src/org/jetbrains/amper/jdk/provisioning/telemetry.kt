/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jdk.provisioning

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer

/**
 * The tracer for the JDK provisioning module.
 */
val OpenTelemetry.tracer: Tracer
    get() = getTracer("org.jetbrains.amper.jdk.provisioning")
