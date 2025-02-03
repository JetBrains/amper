/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.aomBuilder.FragmentSeed
import org.jetbrains.amper.frontend.aomBuilder.adjustSeedsDependencies
import org.jetbrains.amper.frontend.aomBuilder.propagateSettingsForSeeds
import org.jetbrains.amper.frontend.schema.helper.listOfTraceable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class PropagateTest {

    @Test
    fun `basic fragment property propagation`() {
        // given
        val seeds = buildSet {
            seed(Platform.COMMON) {
                kotlin = KotlinSettings().apply {
                    languageVersion = KotlinVersion.Kotlin19
                }
            }
            seed(Platform.JVM)
        }.adjustSeedsDependencies().propagateSettingsForSeeds()

        val jvmSeed = seeds.assertSingleSeed(Platform.JVM)
        assertEquals(KotlinVersion.Kotlin19, jvmSeed.seedSettings?.kotlin?.languageVersion)
    }

    @Test
    fun `multi level propagation`() {
        val seeds = buildSet {
            seed(Platform.COMMON) {
                kotlin = KotlinSettings().apply {
                    languageVersion = KotlinVersion.Kotlin19
                }
            }
            seed(Platform.NATIVE)
            seed(Platform.APPLE)
        }.adjustSeedsDependencies().propagateSettingsForSeeds()

        val darwinFragment = seeds.assertSingleSeed(Platform.APPLE)
        assertEquals(KotlinVersion.Kotlin19, darwinFragment.seedSettings?.kotlin?.languageVersion)
    }

    @Test
    fun `set default values`() {
        val seeds = buildSet {
            seed(Platform.COMMON) {
                kotlin = KotlinSettings().apply {
                    languageVersion = KotlinVersion.Kotlin20
                }
            }
            seed(Platform.JVM)
        }.adjustSeedsDependencies().propagateSettingsForSeeds()

        val jvmFragment = seeds.assertSingleSeed(Platform.JVM)
        assertEquals(KotlinVersion.Kotlin20, jvmFragment.seedSettings?.kotlin?.apiVersion)
    }

    @Test
    fun `artifact receives default values`() {
        val seeds = buildSet {
            seed(Platform.COMMON)
            seed(Platform.JVM)
        }.adjustSeedsDependencies().propagateSettingsForSeeds()

        val jvmFragment = seeds.assertSingleSeed(Platform.JVM)
        assertEquals(KotlinVersion.Kotlin20, jvmFragment.seedSettings?.kotlin?.apiVersion)
    }

    @Test
    fun `android namespace propagation`() {
        val seeds = buildSet {
            seed(Platform.COMMON) {
                android = AndroidSettings().apply {
                    namespace = "namespace"
                }
            }
            seed(Platform.ANDROID)
        }.adjustSeedsDependencies().propagateSettingsForSeeds()

        val androidFragment = seeds.assertSingleSeed(Platform.ANDROID)
        assertEquals("namespace", androidFragment.seedSettings?.android?.namespace)
    }

    @Test
    fun `android params propagation`() {
        val seeds = buildSet {
            seed(Platform.COMMON) {
                android = AndroidSettings().apply {
                    applicationId = "com.example.applicationid"
                    namespace = "com.example.namespace"
                    minSdk = AndroidVersion.VERSION_30
                    maxSdk = AndroidVersion.VERSION_33
                    compileSdk = AndroidVersion.VERSION_33
                    targetSdk = AndroidVersion.VERSION_33
                }
            }
            seed(Platform.ANDROID)
        }.adjustSeedsDependencies().propagateSettingsForSeeds()

        val androidFragment = seeds.assertSingleSeed(Platform.ANDROID)
        val androidSettings = androidFragment.seedSettings?.android

        assertEquals("com.example.applicationid", androidSettings?.applicationId)
        assertEquals("com.example.namespace", androidSettings?.namespace)
        assertEquals(AndroidVersion.VERSION_30, androidSettings?.minSdk)
        assertEquals(AndroidVersion.VERSION_33, androidSettings?.maxSdk)
        assertEquals(AndroidVersion.VERSION_33, androidSettings?.compileSdk)
        assertEquals(AndroidVersion.VERSION_33, androidSettings?.targetSdk)
    }

    @Test
    fun `diamond propagation`() {
        //     common
        //     /    \
        // desktop  apple
        //     \    /
        //    macosX64
        val adjustSeedsDependencies = buildSet {
            seed(Platform.COMMON) {
                kotlin = KotlinSettings().apply {
                    optIns = listOfTraceable("kotlin.contracts.ExperimentalContracts")
                }
            }

            seed(setOf(Platform.MACOS_ARM64, Platform.IOS_ARM64), "apple") {
                kotlin = KotlinSettings().apply {
                    debug = true
                    languageVersion = KotlinVersion.Kotlin18
                }
            }

            seed(setOf(Platform.MACOS_ARM64, Platform.JVM), "desktop") {
                kotlin = KotlinSettings().apply {
                    allWarningsAsErrors = true
                }
            }

            seed(Platform.MACOS_ARM64)
        }.adjustSeedsDependencies()
        val seeds = adjustSeedsDependencies.propagateSettingsForSeeds()


        val kotlinSettings = seeds.assertSingleSeed(Platform.MACOS_ARM64).seedSettings?.kotlin

        assertEquals(
            listOfTraceable("kotlin.contracts.ExperimentalContracts"),
            kotlinSettings?.optIns,
            "should inherit from 'common' ancestor"
        )
        assertEquals(true, kotlinSettings?.debug, "should inherit 'debug' from 'apple' parent")
        assertEquals(
            KotlinVersion.Kotlin18,
            kotlinSettings?.languageVersion,
            "should inherit 'languageVersion' from 'apple' parent"
        )
        assertEquals(true, kotlinSettings?.allWarningsAsErrors, "should inherit from 'desktop' parent")
    }

    private fun Collection<FragmentSeed>.assertSingleSeed(platform: Platform): FragmentSeed {
        return singleOrNull { it.platforms == platform.leaves && it.modifier == "@${platform.pretty}" }
            ?: fail("Expected a single fragment seed with platform '$platform'")
    }

    /**
     * Add a seed by specifying the natural hierarchy platform.
     */
    private fun MutableSet<FragmentSeed>.seed(platform: Platform, init: Settings.() -> Unit = {}): Boolean {
        val platforms = if (platform == Platform.COMMON) Platform.leafPlatforms else platform.leaves
        return add(FragmentSeed(platforms, "@${platform.pretty}", platform).apply {
            seedSettings = Settings().apply(init)
        })
    }

    /**
     * Add a seed by manually specifying leaf platforms and modifiers.
     */
    private fun MutableSet<FragmentSeed>.seed(
        leafPlatforms: Set<Platform>,
        modifier: String,
        init: Settings.() -> Unit = {}
    ): Boolean {
        return add(FragmentSeed(leafPlatforms, modifier, null).apply {
            seedSettings = Settings().apply(init)
        })
    }
}

