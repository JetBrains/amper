/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.executable

import java.nio.file.Path
import kotlin.io.path.Path

data class ExecutableJarConfig(
    /**
     * The application main class name.
     * This will be set as "Start-Class" in the manifest.
     */
    val mainClassName: String? = null,

    /**
     * The base directory for application classes inside the JAR.
     * The default is "BOOT-INF/classes/".
     */
    val classesDirectory: Path = Path("BOOT-INF/classes/"),

    /**
     * The base directory for dependencies inside the JAR.
     * The default is "BOOT-INF/lib/".
     */
    val libDirectory: Path = Path("BOOT-INF/lib/"),

    /**
     * The path for the classpath index file.
     * Default is "BOOT-INF/classpath.idx".
     */
    val classpathIndexPath: Path = Path("BOOT-INF/classpath.idx"),

    /**
     * The path for the layers index file.
     * The default is "BOOT-INF/layers.idx".
     */
    val layersIndexPath: Path = Path("BOOT-INF/layers.idx"),

    /**
     * The Spring Boot loader main class.
     * The default is "org.springframework.boot.loader.launch.JarLauncher".
     */
    val loaderMainClass: String = "org.springframework.boot.loader.launch.JarLauncher",

    /**
     * Additional manifest properties to include.
     */
    val additionalManifestProperties: Map<String, String> = emptyMap()
) {
    /**
     * Returns the default manifest properties required for Spring Boot executable JAR.
     * @return A map of manifest property name to value
     */
    fun convertToManifestProperties(): Map<String, String> = buildMap {
        put("Spring-Boot-Classes", classesDirectory.toString())
        put("Spring-Boot-Lib", libDirectory.toString())
        put("Spring-Boot-Classpath-Index", classpathIndexPath.toString())
        put("Spring-Boot-Layers-Index", layersIndexPath.toString())
        if (mainClassName != null) {
            put("Start-Class", mainClassName)
        }
        putAll(additionalManifestProperties)
    }
}
