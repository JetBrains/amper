/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.diagnostics

import org.jetbrains.amper.dependency.resolution.DependencyResolutionBundle
import org.jetbrains.amper.dependency.resolution.MavenDependency
import org.jetbrains.amper.dependency.resolution.Repository

data class UnableToDownloadFile(
    val fileName: String,
    val dependency: MavenDependency,
    val repositories: List<Repository>,
    val isAutoAddedDocumentation: Boolean = false,
    override val severity: Severity = if (isAutoAddedDocumentation) Severity.INFO else Severity.ERROR,
    override val childMessages: List<Message>,
) : WithChildMessages {
    override val id: String = "unable.to.download.file"
    override val message: String = DependencyResolutionBundle.message(id, fileName, dependency)
    override val shortMessage: String = DependencyResolutionBundle.message("unable.to.download.file.short", fileName)
}
