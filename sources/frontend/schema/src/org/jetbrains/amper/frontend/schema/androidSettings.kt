/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.reportBundleError


enum class AndroidVersion(
    val versionNumber: Int,
    override val outdated: Boolean = false
) : SchemaEnum {
    VERSION_1(1, outdated = true),
    VERSION_2(2, outdated = true),
    VERSION_3(3, outdated = true),
    VERSION_4(4, outdated = true),
    VERSION_5(5, outdated = true),
    VERSION_6(6, outdated = true),
    VERSION_7(7, outdated = true),
    VERSION_8(8, outdated = true),
    VERSION_9(9, outdated = true),
    VERSION_10(10, outdated = true),
    VERSION_11(11, outdated = true),
    VERSION_12(12, outdated = true),
    VERSION_13(13, outdated = true),
    VERSION_14(14, outdated = true),
    VERSION_15(15, outdated = true),
    VERSION_16(16, outdated = true),
    VERSION_17(17, outdated = true),
    VERSION_18(18, outdated = true),
    VERSION_19(19, outdated = true),
    VERSION_20(20, outdated = true),
    VERSION_21(21, outdated = true),
    VERSION_22(22, outdated = true),
    VERSION_23(23, outdated = true),
    VERSION_24(24, outdated = true),
    VERSION_25(25, outdated = true),
    VERSION_26(26),
    VERSION_27(27),
    VERSION_28(28),
    VERSION_29(29),
    VERSION_30(30),
    VERSION_31(31),
    VERSION_32(32),
    VERSION_33(33),
    VERSION_34(34),;

    override val schemaValue = versionNumber.toString()
    val withPrefix = "android-$schemaValue"
    companion object Index : EnumMap<AndroidVersion, String>(AndroidVersion::values, AndroidVersion::schemaValue)
}

class AndroidSettings : SchemaNode() {
    var compileSdk by value<AndroidVersion>().default(AndroidVersion.VERSION_34)
    var minSdk by value<AndroidVersion>().default(AndroidVersion.VERSION_21)
    var maxSdk by value<AndroidVersion>().default(AndroidVersion.VERSION_34)
    var targetSdk by value<AndroidVersion>().default(AndroidVersion.VERSION_34)
    var namespace by nullableValue<String>()
    var applicationId by nullableValue<String>().default { namespace }

    context(ProblemReporterContext) override fun validate() {
        // Check that used android versions are not too old.
        val usedVersions = listOf(::compileSdk, ::minSdk, ::maxSdk, ::targetSdk)
        val oldVersions = usedVersions.filter { it.get() < AndroidVersion.VERSION_21 }
        oldVersions.forEach {
            SchemaBundle.reportBundleError(
                it,
                "too.old.android.version",
                it.get(),
            )
        }
    }
}