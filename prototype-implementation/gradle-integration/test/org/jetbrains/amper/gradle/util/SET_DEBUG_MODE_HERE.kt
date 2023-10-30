/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.util

/**
 * A flag to enter debug mode in GradleRunner.
 * Also applies some hacks, see [bypassTestkitDebugEnvRestriction.kt]
 * and [setUpGradleProjectDir].
 */
const val withDebug = false