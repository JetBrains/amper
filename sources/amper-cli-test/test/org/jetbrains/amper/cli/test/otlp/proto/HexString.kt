/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test.otlp.proto

/**
 * Used to represent protobuf byte arrays, because anyway these values are hex strings in JSON and Java objects.
 */
typealias HexString = String