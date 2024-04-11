/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
 * A way of rude tuning resulting json schema.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CustomSchemaDef(
    val json: String
)

/**
 * A way of rude tuning resulting json schema.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AdditionalSchemaDef(
    val json: String,
    val useOneOf: Boolean = false
)

/**
 * This annotation can be used to indicate that the order in which the enumeration constants
 * are declared is important.
 * This will be utilized in JSON schema by setting the meta property `x-intellij-enum-order-sensitive` to `true`.
 *
 * [reverse] parameter can be used to sort values in the reverse order.
 * This is useful for versions, which should be compared in the natural order but displayed in the reversed one.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class EnumOrderSensitive(
    val reverse: Boolean = false,
)