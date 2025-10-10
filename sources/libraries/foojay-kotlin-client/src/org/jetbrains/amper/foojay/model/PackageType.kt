/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.foojay.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The type of Java package: JRE or JDK.
 */
@Serializable
enum class PackageType(val apiValue: String) {
    @SerialName("jre")
    JRE("jre"),
    @SerialName("jdk")
    JDK("jdk"),
}
