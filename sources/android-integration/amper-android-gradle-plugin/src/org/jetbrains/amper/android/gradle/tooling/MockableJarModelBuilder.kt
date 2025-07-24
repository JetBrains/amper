/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.android.gradle.tooling

import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.publishing.AndroidArtifacts.MOCKABLE_JAR_RETURN_DEFAULT_VALUES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.TYPE_MOCKABLE_JAR
import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.jetbrains.amper.android.MockableJarModel
import java.io.File


data class DefaultMockableJarModel(override val file: File?) : MockableJarModel


class MockableJarModelBuilder : ToolingModelBuilder {
    override fun canBuild(modelName: String): Boolean = modelName == MockableJarModel::class.java.name

    override fun buildAll(modelName: String, project: Project): MockableJarModel =
        DefaultMockableJarModel(
            project
                .configurations
                .named(VariantDependencies.CONFIG_NAME_ANDROID_APIS)
                .get()
                .incoming
                .artifactView {
                    it.attributes { attributeContainer ->
                        attributeContainer
                            .attribute(ARTIFACT_TYPE_ATTRIBUTE, TYPE_MOCKABLE_JAR)
                            .attribute(MOCKABLE_JAR_RETURN_DEFAULT_VALUES, true)
                    }
                }
                .files
                .toList()
                .firstOrNull()
        )
}
