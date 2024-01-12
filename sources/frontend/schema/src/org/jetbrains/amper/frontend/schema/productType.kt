/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.unsafe
import org.jetbrains.amper.frontend.reportBundleError

enum class ProductType(
    override val schemaValue: String,
    val supportedPlatforms: Set<Platform>,
    val defaultPlatforms: Set<Platform>?
) : SchemaEnum {
    LIB(
        "lib",
        supportedPlatforms = Platform.leafPlatforms,
        defaultPlatforms = null
    ),
    LEGACY_APP(
        "app",
        supportedPlatforms = Platform.leafPlatforms,
        defaultPlatforms = setOf(Platform.JVM)
    ),
    JVM_APP(
        "jvm/app",
        supportedPlatforms = setOf(Platform.JVM),
        defaultPlatforms = setOf(Platform.JVM)
    ),
    ANDROID_APP(
        "android/app",
        supportedPlatforms = setOf(Platform.ANDROID),
        defaultPlatforms = setOf(Platform.ANDROID)
    ),
    IOS_APP(
        "ios/app",
        supportedPlatforms = setOf(Platform.IOS_ARM64, Platform.IOS_X64, Platform.IOS_SIMULATOR_ARM64),
        defaultPlatforms = setOf(Platform.IOS_ARM64, Platform.IOS_X64, Platform.IOS_SIMULATOR_ARM64)
    ),
    MACOS_APP(
        "macos/app",
        supportedPlatforms = setOf(Platform.MACOS_X64, Platform.MACOS_ARM64),
        defaultPlatforms = setOf(Platform.MACOS_X64, Platform.MACOS_ARM64)
    ),
    LINUX_APP(
        "linux/app",
        supportedPlatforms = setOf(Platform.LINUX_X64, Platform.LINUX_ARM64),
        defaultPlatforms = setOf(Platform.LINUX_X64, Platform.LINUX_ARM64)
    );

    override fun toString() = schemaValue

    companion object : EnumMap<ProductType, String>(ProductType::values, ProductType::schemaValue)
}

class ModuleProduct : SchemaNode() {
    var type by value<ProductType>()

    var platforms by value<List<Platform>>()
        .default { ::type.unsafe?.defaultPlatforms?.toList() }

    context(ProblemReporterContext)
    override fun validate() {
        // Check empty platforms.
        if (::platforms.unsafe?.isEmpty() == true)
            SchemaBundle.reportBundleError(
                ::platforms,
                "product.platforms.should.not.be.empty",
                level = Level.Fatal
            )

        // Check no platforms for lib.
        if (::type.unsafe == ProductType.LIB && ::platforms.unsafe == null)
            SchemaBundle.reportBundleError(
                ::type,
                "product.type.does.not.have.default.platforms",
                ProductType.LIB.schemaValue,
                level = Level.Fatal
            )

        // Check supported platforms.
        ::platforms.unsafe.orEmpty().forEach { platform ->
            if (platform !in type.supportedPlatforms)
                SchemaBundle.reportBundleError(
                    ::platforms,
                    "product.unsupported.platform",
                    type.schemaValue,
                    platform.pretty,
                    type.supportedPlatforms.joinToString { it.pretty },
                )
        }
    }
}