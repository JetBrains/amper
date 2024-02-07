/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core

import java.util.*

/**
 * Setup IntelliJ platform for Amper in a current process
 */
object IntelliJPlatformInitializer {
    fun setup() {
        setupRegistry()
    }

    private fun setupRegistry() {
        // We do not load IntelliJ platform's component/service management
        // So Registry is not available as in full-blown IDE
        // Set some system properties required for PSI and other stuff we use from platform
        // If you see in output 'WARN: Attempt to load key 'xxx' for not yet loaded registry' please add
        // values to KNOWN_REGISTRY_KEYS_TO_INITIALIZE
        // TODO automatically assert that in integration tests on Amper CLI

        val registryProperties = Properties()
        val urls = javaClass.classLoader.getResources("misc/registry.properties").toList()
        for (resourceUrl in urls) {
            resourceUrl.openStream().use {
                registryProperties.load(it)
            }
        }
        check(!registryProperties.isEmpty) {
            "Unable to find even on 'misc/registry.properties' in classpath"
        }

        for (key in KNOWN_REGISTRY_KEYS_TO_INITIALIZE) {
            val value = registryProperties.getProperty(key)
                ?: error("Unable to find key '$key' in the following property files:\n" + urls.joinToString("\n"))
            System.setProperty(key, value)
        }
    }

    private val KNOWN_REGISTRY_KEYS_TO_INITIALIZE = arrayOf(
        "ide.hide.excluded.files",
        "psi.sleep.in.validity.check",
        "psi.incremental.reparse.depth.limit",
    )
}