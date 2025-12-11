/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jdk.provisioning

import kotlinx.serialization.Serializable
import org.jetbrains.amper.frontend.schema.JdkSelectionMode
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.annotations.Nls

/**
 * The result of a JDK provisioning operation.
 */
@Serializable
sealed interface JdkResult {

    /**
     * A matching JDK is available.
     */
    @Serializable
    sealed interface Success : JdkResult {
        /**
         * The JDK to use.
         */
        val jdk: Jdk
    }

    /**
     * A matching JDK could be found locally (in `JAVA_HOME`).
     */
    @Serializable
    data class FoundInJavaHome(
        /**
         * The JDK that was found locally.
         */
        override val jdk: Jdk,
    ) : Success

    /**
     * A matching JDK could be provisioned (either downloaded or from cache).
     */
    @Serializable
    data class Provisioned(
        /**
         * The provisioned JDK.
         */
        override val jdk: Jdk,
        /**
         * Information about why we couldn't use `JAVA_HOME` and had to fall back to provisioning (in
         * [JdkSelectionMode.auto]), or null if we didn't even attempt it (in [JdkSelectionMode.alwaysProvision]).
         */
        val unusableJavaHomeResult: UnusableJavaHomeResult?,
    ) : Success

    /**
     * No JDK matching the given criteria could be retrieved.
     *
     * This can happen for various reasons, which are reported using the [ProblemReporter].
     * Note: `JAVA_HOME`-related warnings or errors are reported only once per instance of [JdkProvider].
     *
     * The returned [message] is the final failure message that we can fail with.
     * It may invite the user to look at previous error messages, so make sure those are available in a user-visible
     * place.
     */
    @Serializable
    data class Failure(val message: @Nls String) : JdkResult
}

/**
 * Describes why `JAVA_HOME` cannot be used.
 */
@Serializable
sealed interface UnusableJavaHomeResult {

    /**
     * `JAVA_HOME` is not set, or is set to the empty string.
     */
    @Serializable
    data object UnsetOrEmpty : UnusableJavaHomeResult

    /**
     * `JAVA_HOME` is set to a valid JDK, but this JDK doesn't match the criteria for the given [reason].
     */
    @Serializable
    data class Mismatch(val reason: @Nls String) : UnusableJavaHomeResult

    /**
     * `JAVA_HOME` is invalid (e.g., invalid path, doesn't exist, is not a directory, etc.).
     *
     * The exact errors are reported via the [ProblemReporter].
     */
    @Serializable
    data object Invalid : UnusableJavaHomeResult
}

/**
 * Returns the [Jdk] if this result is [JdkResult.Success], or runs the given [fallback] function with the error message.
 */
inline fun JdkResult.orElse(fallback: (errorMessage: String) -> Jdk): Jdk = when (this) {
    is JdkResult.Success -> jdk
    is JdkResult.Failure -> fallback(message)
}

/**
 * Returns the [Jdk] if this result is [JdkResult.Success], or throws a [JdkProvisioningException] otherwise.
 */
fun JdkResult.orThrow(): Jdk = orElse { errorMessage -> throw JdkProvisioningException(errorMessage) }

/**
 * An exception thrown when a JDK provisioning operation fails.
 */
class JdkProvisioningException(message: String) : Exception(message)
