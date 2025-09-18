/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper

/**
 * Applicable to the Paths/Path-referencing properties.
 * Means that the execution avoidance mechanism only considers the value of the Path itself as an input, but ignores the file that it points to, which is neither an [Input] nor an [Output].
 *
 * @see ExecutionAvoidance
 */
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.PROPERTY)
internal annotation class PathValueOnly

/**
 * Means that this value cannot be configured in YAML,
 * but rather is resolved and provided by Amper itself by the moment the task action runs.
 */
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.PROPERTY)
internal annotation class Provided

/**
 * Specifies that polymorphic [Dependency] objects can be constructed from string without using YAML type tags.
 *
 * @see Dependency.Maven.coordinates
 * @see Dependency.Local.modulePath
 */
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.PROPERTY)
internal annotation class DependencyNotation

/**
 * Specifies that the whole object can be constructed from a single YAML value,
 * that is then assigned to the `@Shorthand`-marked property.
 * Other properties in the object have their default values.
 */
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.PROPERTY)
internal annotation class Shorthand