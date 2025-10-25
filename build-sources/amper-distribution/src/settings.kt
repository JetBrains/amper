/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.Configurable
import org.jetbrains.amper.plugins.EnumValue
import java.nio.file.Path

@Configurable
interface DistributionSettings {
    val extraClasspaths: Map<String, Classpath> get() = emptyMap()
    val embedClasspathAsResources: EmbedClasspathAsResources
}

@Configurable
interface EmbedClasspathAsResources {
    val classpath: Classpath
    val resourceDirName: String
}

enum class Repository {
    @EnumValue("maven-local")
    MavenLocal,
    @EnumValue("jetbrains-team-amper-maven")
    JetBrainsTeamAmperMaven,
}

@Configurable
interface Distribution {
    val cliTgz: Path
    val wrappersDir: Path
}
