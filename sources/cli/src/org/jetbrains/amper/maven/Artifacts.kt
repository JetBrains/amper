/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven

import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import java.nio.file.Path
import kotlin.io.path.extension

internal fun Path.toMavenArtifact(
    coords: MavenCoordinates,
    classifier: String = "",
    extension: String = this.extension,
): Artifact = DefaultArtifact(
    coords.groupId,
    coords.artifactId,
    classifier,
    extension,
    coords.version,
).setFile(toFile())
