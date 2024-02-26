/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.AdditionalSchemaDef
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.api.asTraceable
import org.jetbrains.amper.frontend.api.unsafe


@SchemaDoc("Product type to build from the module")
enum class ProductType(
    val value: String,
    val supportedPlatforms: Set<Platform>,
    val defaultPlatforms: Set<Platform>?
): SchemaEnum {

    @SchemaDoc("Kotlin multiplatform library")
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

    @SchemaDoc("JVM application")
    JVM_APP(
        "jvm/app",
        supportedPlatforms = setOf(Platform.JVM),
        defaultPlatforms = setOf(Platform.JVM)
    ),

    @SchemaDoc("Android application")
    ANDROID_APP(
        "android/app",
        supportedPlatforms = setOf(Platform.ANDROID),
        defaultPlatforms = setOf(Platform.ANDROID)
    ),

    @SchemaDoc("Ios application")
    IOS_APP(
        "ios/app",
        supportedPlatforms = setOf(Platform.IOS_ARM64, Platform.IOS_X64, Platform.IOS_SIMULATOR_ARM64),
        defaultPlatforms = setOf(Platform.IOS_ARM64, Platform.IOS_X64, Platform.IOS_SIMULATOR_ARM64)
    ),

    @SchemaDoc("Macos application")
    MACOS_APP(
        "macos/app",
        supportedPlatforms = setOf(Platform.MACOS_X64, Platform.MACOS_ARM64),
        defaultPlatforms = setOf(Platform.MACOS_X64, Platform.MACOS_ARM64)
    ),

    @SchemaDoc("Linux application")
    LINUX_APP(
        "linux/app",
        supportedPlatforms = setOf(Platform.LINUX_X64, Platform.LINUX_ARM64),
        defaultPlatforms = setOf(Platform.LINUX_X64, Platform.LINUX_ARM64)
    ),

    @SchemaDoc("Windows application")
    WINDOWS_APP(
        "windows/app",
        supportedPlatforms = setOf(Platform.MINGW_X64),
        defaultPlatforms = setOf(Platform.MINGW_X64),
    );

    fun isLibrary() = this == LIB
    fun isApplication() = !isLibrary()
    override fun toString() = value
    override val schemaValue: String = value
    override val outdated: Boolean = false

    companion object : EnumMap<ProductType, String>(ProductType::values, ProductType::value)
}

@SchemaDoc("Description of the product that should be built from the module")
@AdditionalSchemaDef(productShortForm, useOneOf = true)
class ModuleProduct : SchemaNode() {

    @SchemaDoc("What type of product to generate")
    var type by value<ProductType>()

    @SchemaDoc("What platforms to generate the product for")
    var platforms by value<List<TraceableEnum<Platform>>> { ::type.unsafe?.defaultPlatforms?.toList()?.map(Platform::asTraceable) }
}

const val productShortForm = """
  {
    "enum": ["lib","app","jvm/app","android/app","ios/app","macos/app","linux/app","windows/app"]
  }
"""