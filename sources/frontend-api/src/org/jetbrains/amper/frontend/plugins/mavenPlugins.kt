/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.plugins

/**
 * Interface representing the maven "plugin.xml" structure that is used to:
 * 1. Generate schema types, accessible in the module file.
 * 2. Resolve maven plugin dependencies.
 * 3. Create corresponding tasks.
 */
interface AmperMavenPluginDescription {
    val groupId: String
    val artifactId: String
    val version: String
    val goalPrefix: String
    val mojos: List<AmperMavenPluginMojo>
}

interface AmperMavenPluginMojo {
    val goal: String
    val phase: String?
}