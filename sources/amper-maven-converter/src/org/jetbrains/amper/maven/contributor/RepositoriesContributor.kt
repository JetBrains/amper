/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven.contributor

import org.apache.maven.project.MavenProject
import org.jetbrains.amper.frontend.schema.Base
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Repository
import org.jetbrains.amper.maven.ProjectTreeBuilder
import kotlin.io.path.div


internal fun ProjectTreeBuilder.contributeRepositories(reactorProjects: Set<MavenProject>) {
    for (project in reactorProjects.filterJarProjects()) {
        module(project.basedir.toPath() / "module.yaml") {
            withDefaultContext {
                `object`<Module> {
                    project.distributionManagement?.repository?.let { repo ->
                        Repository::id setTo scalar(repo.id)
                        Repository::url setTo scalar(repo.url)
                        Repository::publish setTo scalar(true)
                    }
                    project.distributionManagement?.snapshotRepository?.let { repo ->
                        Repository::id setTo scalar(repo.id)
                        Repository::url setTo scalar(repo.url)
                        Repository::publish setTo scalar(true)
                    }
                    project.remoteProjectRepositories.forEach { repo ->
                        if (repo.id == "central") return@forEach
                        Base::repositories {
                            add(`object`<Repository> {
                                Repository::id setTo scalar(repo.id)
                                Repository::url setTo scalar(repo.url)
                            })
                        }
                    }
                }
            }
        }
    }
}