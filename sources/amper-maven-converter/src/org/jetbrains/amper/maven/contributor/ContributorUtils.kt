/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven.contributor

import org.apache.maven.project.MavenProject

class MavenRootNotFoundException(potentialRoots: Set<MavenProject>) :
    Exception("No root maven module found: ${potentialRoots.joinToString(", ")}")

internal fun Set<MavenProject>.filterJarProjects(): Set<MavenProject> = filter { it.packaging == "jar" }.toSet()
