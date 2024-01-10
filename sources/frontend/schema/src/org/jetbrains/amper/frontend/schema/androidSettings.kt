/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.SchemaEnum
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.reportBundleError


enum class AndroidVersion(
    val versionNumber: Int,
) : SchemaEnum {
    VERSION_1(1),
    VERSION_2(2),
    VERSION_3(3),
    VERSION_4(4),
    VERSION_5(5),
    VERSION_6(6),
    VERSION_7(7),
    VERSION_8(8),
    VERSION_9(9),
    VERSION_10(10),
    VERSION_11(11),
    VERSION_12(12),
    VERSION_13(13),
    VERSION_14(14),
    VERSION_15(15),
    VERSION_16(16),
    VERSION_17(17),
    VERSION_18(18),
    VERSION_19(19),
    VERSION_20(20),
    VERSION_21(21),
    VERSION_22(22),
    VERSION_23(23),
    VERSION_24(24),
    VERSION_25(25),
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