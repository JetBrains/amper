/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven.contributor

import org.apache.maven.project.MavenProject
import org.jetbrains.amper.frontend.schema.AmperLayout
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.tree.invoke
import org.jetbrains.amper.frontend.types.generated.*
import org.jetbrains.amper.maven.ProjectTreeBuilder
import kotlin.io.path.div

internal fun ProjectTreeBuilder.contributeCoreModule(reactorProjects: Set<MavenProject>) {
    for (project in reactorProjects.filterJarProjects()) {
        module(project.basedir.toPath() / "module.yaml") {
            withDefaultContext {
                product {
                    type(ProductType.JVM_LIB)
                }
                layout(AmperLayout.MAVEN_LIKE)
                settings {
                    publishing {
                        enabled(true)
                        name(project.model.artifactId)
                        group(project.model.groupId)
                        version(project.model.version)
                    }
                }
            }
        }
    }
}
