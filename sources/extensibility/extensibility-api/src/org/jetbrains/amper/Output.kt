/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper

/**
 * Marks a [java.nio.file.Path] parameter of a [TaskAction]-annotated method.
 * Signals the framework that the file/directory located by this path is an output of this task action.
 *
 * Dependency wiring will rely on this information: the task, that has this path as an [Input], will depend
 * on the task with this action.
 *
 * Execution avoidance mechanism may rely on this information.
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Output
