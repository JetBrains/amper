/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.EnumOrderSensitive
import org.jetbrains.amper.frontend.api.EnumValueFilter
import org.jetbrains.amper.frontend.api.Misnomers
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Shorthand
import org.jetbrains.amper.frontend.api.asTraceable
import org.jetbrains.amper.frontend.api.schemaDelegate

@SchemaDoc("Defines what should be produced out of the module. Read more about the [product types](#product-types)")
@EnumValueFilter("outdated", isNegated = true)
@EnumOrderSensitive
enum class ProductType(
    val value: String,
    val supportedPlatforms: Set<Platform>,
    val defaultPlatforms: Set<Platform>?,
    override val outdated: Boolean = false
): SchemaEnum {

    @SchemaDoc("A reusable library which could be used as dependency by other modules in the codebase")
    LIB(
        "lib",
        supportedPlatforms = Platform.leafPlatforms,
        defaultPlatforms = null
    ),

    LEGACY_APP(
        "app",
        supportedPlatforms = Platform.leafPlatforms,
        defaultPlatforms = setOf(Platform.JVM),
        outdated = true,
    ),

    @SchemaDoc("A JVM console or desktop application")
    JVM_APP(
        "jvm/app",
        supportedPlatforms = setOf(Platform.JVM),
        defaultPlatforms = setOf(Platform.JVM)
    ),

    @SchemaDoc("An Amper plugin")
    JVM_AMPER_PLUGIN(
        "jvm/amper-plugin",
        supportedPlatforms = setOf(Platform.JVM),
        defaultPlatforms = setOf(Platform.JVM),
    ),

    @SchemaDoc("An Android VM application")
    ANDROID_APP(
        "android/app",
        supportedPlatforms = setOf(Platform.ANDROID),
        defaultPlatforms = setOf(Platform.ANDROID)
    ),

    @SchemaDoc("An iOS application")
    IOS_APP(
        "ios/app",
        supportedPlatforms = setOf(Platform.IOS_ARM64, Platform.IOS_X64, Platform.IOS_SIMULATOR_ARM64),
        defaultPlatforms = setOf(Platform.IOS_ARM64, Platform.IOS_X64, Platform.IOS_SIMULATOR_ARM64)
    ),

    @SchemaDoc("A native macOS application")
    MACOS_APP(
        "macos/app",
        supportedPlatforms = setOf(Platform.MACOS_X64, Platform.MACOS_ARM64),
        defaultPlatforms = setOf(Platform.MACOS_X64, Platform.MACOS_ARM64)
    ),

    @SchemaDoc("A native linux application")
    LINUX_APP(
        "linux/app",
        supportedPlatforms = setOf(Platform.LINUX_X64, Platform.LINUX_ARM64),
        defaultPlatforms = setOf(Platform.LINUX_X64, Platform.LINUX_ARM64)
    ),

    @SchemaDoc("A native Windows application")
    WINDOWS_APP(
        "windows/app",
        supportedPlatforms = setOf(Platform.MINGW_X64),
        defaultPlatforms = setOf(Platform.MINGW_X64),
    ),

    @SchemaDoc("A wasm JS application")
    WASM_JS_APP(
        "wasm-js/app",
        supportedPlatforms = setOf(Platform.WASM_JS),
        defaultPlatforms = setOf(Platform.WASM_JS)
    ),

    @SchemaDoc("A JavaScript application")
    JS_APP(
        "js/app",
        supportedPlatforms = setOf(Platform.JS),
        defaultPlatforms = setOf(Platform.JS)
    ),;

    fun isLibrary() = this == LIB || this == JVM_AMPER_PLUGIN
    fun isApplication() = !isLibrary()
    override fun toString() = value
    override val schemaValue: String = value

    companion object : EnumMap<ProductType, String>(ProductType::values, ProductType::value)
}

@SchemaDoc("Defines what should be produced out of the module. [Read more](#product-types)")
class ModuleProduct : SchemaNode() {

    @Misnomers("application", "library")
    @Shorthand
    @SchemaDoc("What type of product to generate")
    var type by value<ProductType>()

    @SchemaDoc("What platforms to generate the product for")
    var platforms by dependentValue(::type) { productType ->
        productType.defaultPlatforms?.map { it.asTraceable(DefaultTrace(computedValueTrace = ::type.schemaDelegate)) }
            // Degenerate case when there are no default platforms but also platforms are not declared by the user.
            // It is reported as an error via diagnostics, so it will never reach AOM consumers, and thus it's better
            // to keep this type non-nullable.
            // Note that this empty list can still be obtained when running other diagnostics. This is actually
            // desirable because it correctly represents the fact that we have no platforms at all (thus aliases or
            // @Platform-specific property usages will be properly reported).
            ?: emptyList()
    }
}
