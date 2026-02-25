/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven.contributor

import org.apache.maven.project.MavenProject
import org.jetbrains.amper.frontend.tree.add
import org.jetbrains.amper.frontend.tree.invoke
import org.jetbrains.amper.frontend.types.generated.*
import org.jetbrains.amper.maven.ProjectTreeBuilder
import kotlin.io.path.div

internal fun ProjectTreeBuilder.contributeRepositories(reactorProjects: Set<MavenProject>) {
    for (project in reactorProjects.filterJarProjects()) {
        module(project.basedir.toPath() / "module.yaml") {
            withDefaultContext {
                repositories(skipIfEmpty = true) {
                    project.distributionManagement?.repository?.let { repo ->
                        add {
                            id(repo.id)
                            url(repo.url)
                            publish(true)
                        }
                    }
                    project.distributionManagement?.snapshotRepository?.let { repo ->
                        add {
                            id(repo.id)
                            url(repo.url)
                            publish(true)
                        }
                    }
                    project.remoteProjectRepositories.forEach { repo ->
                        if (repo.id == "central") return@forEach
                        add {
                            id(repo.id)
                            url(repo.url)
                        }
                    }
                }
            }
        }
    }
}