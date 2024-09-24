/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jar

import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.io.path.outputStream

// TODO support signing?
// TODO support for Zip64 to allow many many classes?
@Serializable
data class JarConfig(
    /**
     * Base archive config.
     */
    val zipConfig: ZipConfig = ZipConfig(),
    /**
     * The fully qualified name of a class to specify as Main-Class attribute in the jar manifest.
     * This is necessary to create executable jars.
     */
    val mainClassFqn: String? = null,
)

/**
 * Same as [writeZip] but with JAR-specific config.
 */
fun Path.writeJar(inputs: List<ZipInput>, config: JarConfig) {
    val manifest = createManifest(config)
    JarOutputStream(outputStream(), manifest).use { out ->
        out.writeZip(inputs, config.zipConfig)
    }
}

private fun createManifest(config: JarConfig): Manifest = Manifest().apply {
    mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
    if (config.mainClassFqn != null) {
        mainAttributes[Attributes.Name.MAIN_CLASS] = config.mainClassFqn
    }
}
