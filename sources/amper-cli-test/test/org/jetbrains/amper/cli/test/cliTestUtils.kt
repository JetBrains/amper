/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import io.opentelemetry.api.common.AttributeKey
import org.jetbrains.amper.test.spans.FilteredSpans
import org.jetbrains.amper.test.spans.SpansTestCollector
import org.jetbrains.amper.test.spans.spansNamed


internal val SpansTestCollector.xcodebuildSpans: FilteredSpans
    get() = spansNamed("xcodebuild")

internal val SpansTestCollector.iosKotlinTests: FilteredSpans
    get() = spansNamed("ios-kotlin-test")

internal val SpansTestCollector.konancSpans: FilteredSpans
    get() = spansNamed("konanc")

internal val SpansTestCollector.xcodeProjectGenSpans
    get() = spansNamed("xcode project generation")

internal val SpansTestCollector.xcodeProjectManagementSpans
    get() = spansNamed("xcode project management")

internal val UpdatedAttribute = AttributeKey.booleanKey("updated")