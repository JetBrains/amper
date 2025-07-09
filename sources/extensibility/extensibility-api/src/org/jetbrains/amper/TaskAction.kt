/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper

/**
 * Annotates a *top-level* Kotlin function, marking it as a possible task action.
 * Such a function must return [Unit] and have any number of parameters. The allowed parameter types are the same as
 * for properties in [Schema]-annotated interfaces.
 *
 * In addition, [java.nio.file.Path] parameters must be annotated with either [Input] or [Output].
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class TaskAction
