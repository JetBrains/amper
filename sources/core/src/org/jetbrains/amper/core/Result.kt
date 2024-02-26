/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core

sealed interface Result<out T> {
    companion object {
        fun <T> success(value: T): Result<T> = Success(value)
        fun <T> failure(exception: Throwable): Result<T> = Failure(exception)
    }

    data class Success<T>(val value: T) : Result<T>
    data class Failure<T>(val exception: Throwable) : Result<T>
}

fun <T> T.asAmperSuccess() = Result.success(this)

fun <T> Result<T>.get(): T = when (this) {
    is Result.Success -> value
    is Result.Failure -> throw exception
}

fun <T> Result<T>.getOrNull(): T? = when (this) {
    is Result.Success -> value
    is Result.Failure -> null
}

inline fun <R, T : R> Result<T>.getOrElse(onFailure: (Throwable) -> R): R = when (this) {
    is Result.Success -> value
    is Result.Failure -> onFailure(exception)
}

inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.success(transform(value))
    is Result.Failure -> Result.failure(exception)
}

inline fun <T, R, V : Result<R>?> Result<T>.flatMap(transform: (T) -> V): V = when (this) {
    is Result.Success -> transform(value)
    is Result.Failure -> Result.failure<R>(exception) as V
}

fun <T> List<Result<T>>.unwrap(): List<T> = map { it.get() }
