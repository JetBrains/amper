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
interface MavenPluginXml {
    val name: String
    val description: String
    val groupId: String
    val artifactId: String
    val version: String
    val goalPrefix: String
    val isolatedRealm: Boolean
    val inheritedByDefault: Boolean
    val requiredJavaVersion: String
    val requiredMavenVersion: String
    val mojos: List<Mojo>
    val dependencies: List<Dependency>
}

interface Mojo {
    val goal: String
    val phase: String?
    val description: String
    val requiresDirectInvocation: Boolean
    val requiresProject: Boolean
    val requiresReports: Boolean
    val aggregator: Boolean
    val requiresOnline: Boolean
    val inheritedByDefault: Boolean
    val implementation: String
    val language: String
    val instantiationStrategy: String
    val executionStrategy: String
    val threadSafe: Boolean
    val parameters: List<Parameter>
}

interface Parameter {
    val name: String
    val type: String
    val required: Boolean
    val editable: Boolean
    val description: String
}

interface Dependency {
    val groupId: String
    val artifactId: String
    val version: String
    val type: String
}