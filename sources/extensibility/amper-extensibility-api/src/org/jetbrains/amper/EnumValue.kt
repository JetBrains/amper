/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper

/**
 * Specifies a string value for the enum constant to be used in YAML build files instead of the default one.
 *
 * NOTE: The default enum value in YAML is constant's name converted into a "kebab-case",
 * e.g., `MyConstant` and `MY_CONSTANT` become `my-constant` in YAML.
 */
@MustBeDocumented
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FIELD)
annotation class EnumValue(val value: String)
