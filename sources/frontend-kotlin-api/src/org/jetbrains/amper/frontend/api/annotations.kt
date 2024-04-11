/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.api

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