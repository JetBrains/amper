/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.TraceableString

class JvmTestSettings : SchemaNode() {
    @SchemaDoc("Pass JVM system properties to set for the test process.")
    var systemProperties by value<Map<TraceableString, TraceableString>>(default = emptyMap())

    @SchemaDoc("Pass any JVM command line arguments to the test process.")
    var freeJvmArgs by value<List<String>>(default = emptyList())
}
