/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.api

/**
 * An annotation to specify documentation bundle entry.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class SchemaDoc(
//    val bundleKey: String

    // TODO Replace by bundle key.
    val doc: String
)

/**
 * Special marker to highlight that this property can
 * have "@" modifier.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ModifierAware

/**
 * Mark, that this class should not be visible within schema and documentation
 * and its field should be embedded in its parent.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Embedded