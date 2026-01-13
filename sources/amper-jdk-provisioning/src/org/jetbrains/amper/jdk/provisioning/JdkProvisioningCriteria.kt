/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jdk.provisioning

import kotlinx.serialization.Serializable
import org.jetbrains.amper.frontend.schema.JvmDistribution
import org.jetbrains.amper.system.info.Arch
import org.jetbrains.amper.system.info.OsFamily

@Serializable
data class JdkProvisioningCriteria(
    val majorVersion: Int,
    val distributions: List<JvmDistribution>? = null,
    val acknowledgedLicenses: List<JvmDistribution> = emptyList(),
    val operatingSystems: List<OsFamily> = listOf(OsFamily.current),
    val architectures: List<Arch> = listOf(Arch.current),
)
