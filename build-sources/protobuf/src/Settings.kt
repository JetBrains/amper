/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins.protobuf

import org.jetbrains.amper.plugins.Configurable

@Configurable
interface Settings {
    /**
     * `protoc` tool version to use.
     * The tool is currently downloaded from the corresponding maven artifact.
     */
    val protocVersion: String get() = "3.25.8"

    /**
     * GRPC settings.
     */
    val grpc: Grpc
}

@Configurable
interface Grpc {
    /**
     * Whether gRPC plugin should be provisioned and enabled.
     */
    val enabled: Boolean get() = false

    /**
     * gRPC plugin version to use.
     * The plugin is currently downloaded from the corresponding maven artifact.
     */
    val version get() = "1.76.0"
}
