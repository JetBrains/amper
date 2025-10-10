/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.foojay.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A JDK or JRE distribution.
 *
 * The JVM is implemented by many different vendors, and each of them creates one or more distributions.
 * Some of them are OpenJDK implementations, some of them are proprietary and subject to particular licensing.
 */
@Serializable
enum class Distribution(val apiValue: String) {
    @SerialName("bisheng")
    BISHENG(apiValue = "bisheng"),
    @SerialName("corretto")
    CORRETTO(apiValue = "corretto"),
    @SerialName("dragonwell")
    DRAGONWELL(apiValue = "dragonwell"),
    @SerialName("jetbrains")
    JETBRAINS(apiValue = "jetbrains"),
    @SerialName("kona")
    KONA(apiValue = "kona"),
    @SerialName("liberica")
    LIBERICA(apiValue = "liberica"),
    @SerialName("microsoft")
    MICROSOFT(apiValue = "microsoft"),
    @SerialName("openlogic")
    OPEN_LOGIC(apiValue = "openlogic"),
    @SerialName("oracle")
    ORACLE(apiValue = "oracle"),
    @SerialName("oracle_open_jdk")
    ORACLE_OPEN_JDK(apiValue = "oracle_open_jdk"),
    @SerialName("sap_machine")
    SAP_MACHINE(apiValue = "sap_machine"),
    @SerialName("semeru")
    SEMERU(apiValue = "semeru"),
    @SerialName("semeru_certified")
    SEMERU_CERTIFIED(apiValue = "semeru_certified"),
    @SerialName("temurin")
    TEMURIN(apiValue = "temurin"),
    @SerialName("zulu")
    ZULU(apiValue = "zulu"),
    @SerialName("zulu_prime")
    ZULU_PRIME(apiValue = "zulu_prime"),
}
