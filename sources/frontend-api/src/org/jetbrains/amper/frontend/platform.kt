/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.Platform.Companion.naturalHierarchy
import org.jetbrains.amper.frontend.api.EnumValueFilter
import org.jetbrains.amper.frontend.api.TraceableEnum

/**
 * Enum, that describes the concrete platform the sources are built for.
 *
 * [parent] — Parent of the platform in natural KMP hierarchy.
 * [isLeaf] — Helper flag to indicate that the platform is a leaf in the hierarchy.
 */
@EnumValueFilter("isLeaf")
enum class Platform(
    override val parent: Platform? = null,
    override val isLeaf: Boolean = false,
    override val outdated: Boolean = false
) : SchemaEnum, Context {
    COMMON,

    JS(COMMON, isLeaf = true),
    JVM(COMMON, isLeaf = true),
    WASM(COMMON, isLeaf = true),
    ANDROID(COMMON, isLeaf = true),
    NATIVE(COMMON),

    LINUX(NATIVE),
    LINUX_X64(LINUX, isLeaf = true),
    LINUX_ARM64(LINUX, isLeaf = true),

    APPLE(NATIVE),

    TVOS(APPLE),
    TVOS_ARM64(TVOS, isLeaf = true),
    TVOS_X64(TVOS, isLeaf = true),
    TVOS_SIMULATOR_ARM64(TVOS, isLeaf = true),

    MACOS(APPLE),
    MACOS_X64(MACOS, isLeaf = true),
    MACOS_ARM64(MACOS, isLeaf = true),

    IOS(APPLE),
    IOS_ARM64(IOS, isLeaf = true),
    IOS_SIMULATOR_ARM64(IOS, isLeaf = true),
    IOS_X64(IOS, isLeaf = true),

    WATCHOS(APPLE),
    WATCHOS_ARM64(WATCHOS, isLeaf = true),
    WATCHOS_ARM32(WATCHOS, isLeaf = true),
    WATCHOS_DEVICE_ARM64(WATCHOS, isLeaf = true),
    WATCHOS_SIMULATOR_ARM64(WATCHOS, isLeaf = true),

    MINGW(NATIVE),
    MINGW_X64(MINGW, isLeaf = true),

    ANDROID_NATIVE(NATIVE),
    ANDROID_NATIVE_ARM32(ANDROID_NATIVE, isLeaf = true),
    ANDROID_NATIVE_ARM64(ANDROID_NATIVE, isLeaf = true),
    ANDROID_NATIVE_X64(ANDROID_NATIVE, isLeaf = true),
    ANDROID_NATIVE_X86(ANDROID_NATIVE, isLeaf = true),;

    // TODO Copy pasted. Check why NoSuchMethodError arises when using outer method.
    private val prettyRegex = "_.".toRegex()
    private fun String.doCamelCase() = this.lowercase().replace(prettyRegex) { it.value.removePrefix("_").uppercase() }
    override val pretty get() = name.doCamelCase()

    override val schemaValue: String = pretty

    /**
     * The name of this target platform as defined by the Kotlin compiler.
     *
     * * It should match the values of the `-target` argument of the Kotlin/Native compiler.
     * * It should match the names of the platforms in the .konan directory.
     */
    val nameForCompiler: String
        get() = name.lowercase()

    /**
     * Get leaf children of this parent if it is a parent; List of self otherwise.
     */
    override val leaves: Set<Platform> by lazy {
        if (isLeaf) setOf(this)
        else naturalHierarchy[this] ?: error("Platform $this is not a leaf platform and doesn't have a hierarchy")
    }

    val topmostParentNoCommon by lazy { generateSequence(this) { it.parentNoCommon }.last() }

    val pathToParent by lazy { generateSequence(this) { it.parent }.toSet() }

    val parentNoCommon by lazy { parent?.takeIf { it != COMMON } }

    companion object : EnumMap<Platform, String>(Platform::values, { pretty }) {

        val docsUrl = "https://github.com/JetBrains/amper/blob/HEAD/docs/Documentation.md#multiplatform-projects"

        val leafPlatforms: Set<Platform> = Platform.values.filterTo(mutableSetOf()) { it.isLeaf }

        /**
         * Parent-child relations throughout parent hierarchy for every leaf child.
         * For example, **`MACOS/[ MACOS_X64, ... ]`** and **`APPLE/[ MACOS_X64, ... ]`** are both present.
         *
         * Leaf platforms are **not present** (for example, **`IOS_ARM64/[ IOS_ARM64 ]`** is not in the map).
         *
         * Hierarchy **doest not include** COMMON platform.
         */
        val naturalHierarchy: Map<Platform, Set<Platform>> = buildMap<Platform, MutableSet<Platform>> {
            // Add parent-child relation for every parent in hierarchy.
            fun add(parent: Platform?, child: Platform): Unit = if (parent != null && parent != COMMON) {
                this[parent] = (this[parent] ?: mutableSetOf()).apply { add(child) }
                add(parent.parent, child)
            } else Unit
            leafPlatforms.forEach { add(it.parent, it) }
        }

        /**
         * [naturalHierarchy] with [COMMON] and leaves included.
         */
        val naturalHierarchyExt = naturalHierarchy + (COMMON to leafPlatforms) + leafPlatforms.associateWith { setOf(it) }
    }
}

val Iterable<TraceableEnum<Platform>>.leaves get() = flatMap { it.value.leaves }.toSet()

fun Platform.isDescendantOf(other: Platform): Boolean =
    this == other || parent?.isDescendantOf(other) == true

fun Platform.isParentOf(other: Platform) = other.isDescendantOf(this)
fun Platform.isParentOfStrict(other: Platform) = this != other && other.isDescendantOf(this)