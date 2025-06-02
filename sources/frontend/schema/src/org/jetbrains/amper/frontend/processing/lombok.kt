/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.schema.MavenJavaAnnotationProcessorDeclaration
import org.jetbrains.amper.frontend.schema.Module

fun Module.configureLombokDefaults() = apply {
    settings.values.forEach { fragmentSettings ->
        if (fragmentSettings.lombok.enabled) {
            val lombokProcessor = MavenJavaAnnotationProcessorDeclaration(
                coordinates = TraceableString("org.projectlombok:lombok:${UsedVersions.lombokVersion}")
            )
            fragmentSettings.java.annotationProcessing.processors += lombokProcessor
        }
    }
}
