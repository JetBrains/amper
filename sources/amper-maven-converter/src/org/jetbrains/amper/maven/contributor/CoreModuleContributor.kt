/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven.contributor

import org.apache.maven.project.MavenProject
import org.jetbrains.amper.frontend.schema.AmperLayout
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.ModuleProduct
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.schema.PublishingSettings
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.maven.ProjectTreeBuilder
import kotlin.io.path.div


internal fun ProjectTreeBuilder.contributeCoreModule(reactorProjects: Set<MavenProject>) {
    for (project in reactorProjects.filterJarProjects()) {
        module(project.basedir.toPath() / "module.yaml") {
            withDefaultContext {
                `object`<Module> {
                    Module::product {
                        ModuleProduct::type setTo scalar(ProductType.JVM_LIB)
                    }
                    Module::layout setTo scalar(AmperLayout.MAVEN_LIKE)
                    Module::settings {
                        Settings::publishing {
                            PublishingSettings::enabled setTo scalar(true)
                            PublishingSettings::name setTo scalar(project.model.artifactId)
                            PublishingSettings::group setTo scalar(project.model.groupId)
                            PublishingSettings::version setTo scalar(project.model.version)
                        }
                    }
                }
            }
        }
    }
}
