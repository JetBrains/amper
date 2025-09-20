/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.amper.Classpath
import org.jetbrains.amper.EnumValue
import org.jetbrains.amper.Schema
import java.nio.file.Path

@Schema
interface DistributionSettings {
    val extraClasspaths: Map<String, Classpath> get() = emptyMap()
    val embedClasspathAsResources: EmbedClasspathAsResources
}

@Schema
interface EmbedClasspathAsResources {
    val classpath: Classpath
    val resourceDirName: String
}

enum class Repository {
    MavenLocal,
    @EnumValue("jetbrains-team-amper-maven")
    JetBrainsTeamAmperMaven,
}

@Schema
interface Distribution {
    val cliTgz: Path
    val wrappersDir: Path
}
