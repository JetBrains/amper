/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.api

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.schema.ProductType

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
 * Defines that this field is a dependency key. Can be either of:
 * - String / TraceableString
 * - TraceablePath
 * - CatalogKey
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class DependencyKey

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

/**
 * This annotation can be used to filter enum values that are propagated into the generated JSON schema
 *
 * [filterPropertyName] property name of the enum class that acts as a filter for values
 * [isNegated] if the filter is negated, e.g., when we want to filter out the obsolete values
 * This property must exist and must be boolean for the filtering to work.
 * 'True' means the value is accepted, and 'false' means that the value is not included in the JSON schema
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class EnumValueFilter(
    val filterPropertyName: String,
    val isNegated: Boolean = false
)

/**
 * This annotation can be used for properties to mark them as platform-specific
 * [platforms] The list of platforms, treated as an OR list
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class PlatformSpecific(
    vararg val platforms: Platform
)

/**
 * This annotation can be used for properties to mark them as product type-specific
 * [productTypes] The list of product types, treated as an OR list
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ProductTypeSpecific(
    vararg val productTypes: ProductType
)

/**
 * This annotation marks a property that is applicable only to a Gradle-based Amper configuration
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class GradleSpecific

/**
 * This annotation marks a property that is applicable only to a standalone Amper configuration
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class StandaloneSpecific

/**
 * This annotation can be used for properties to specify that they can only be used without any modifier.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ContextAgnostic

/**
 * This annotation should be applied to properties
 *  which represent shorthand values for their parent object construction
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Shorthand

/**
 * If we don't want to limit a value by an enum, but we still want to provide code assistance for known values
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class KnownStringValues(vararg val values: String)

/**
 * Aliases for a property. Used mainly in the IDE (code completion)
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Aliases(vararg val values: String)