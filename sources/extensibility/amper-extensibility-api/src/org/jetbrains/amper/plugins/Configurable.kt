/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins

/**
 * Annotation for interfaces that can be used as configuration objects in Amper.
 *
 * The annotated interfaces are only permitted:
 * - to have abstract `val` properties of the following types:
 *   - [Int]
 *   - [String]
 *   - [Boolean]
 *   - [java.nio.file.Path]
 *   - [Configurable]-annotated interfaces
 *   - [List]/[Set] collection of the allowed types
 *   - [Map] of `String` to any allowed type
 * - to extend other [Configurable]-annotated interfaces
 */
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class Configurable
