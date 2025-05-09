/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.artifacts.api

import kotlin.reflect.KClass

@JvmInline
value class ArtifactType<T : Artifact>(
    val clazz: KClass<T>,
)