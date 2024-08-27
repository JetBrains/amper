/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.TraceableString

class KspSettings : SchemaNode() {

    @SchemaDoc("The version of KSP to use")
    var version by value(UsedVersions.kspVersion)

    @SchemaDoc("The list of KSP processors to use (maven coordinates or catalog references)")
    var processors by value<List<TraceableString>>(default = emptyList())

    @SchemaDoc("Some options to pass to KSP processors. Refer to each processor documentation for details.")
    var processorOptions by value<List<TraceableString>>(default = emptyList())
}

/**
 * Whether KSP should be run.
 */
val KspSettings.enabled: Boolean
    get() = processors.isNotEmpty()

// TODO make options a Map in the first place
val KspSettings.processorOptionsAsMap: Map<String, String>
    get() = processorOptions.associate {
        require("=" in it.value) { "KSP processor options must be passed as 'key=value'" }
        val (key, value) = it.value.split("=")
        key.trim() to value.trim()
    }
