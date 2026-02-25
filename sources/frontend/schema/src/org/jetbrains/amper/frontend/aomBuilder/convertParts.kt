/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.frontend.ClassBasedSet
import org.jetbrains.amper.frontend.GitSourcesModulePart
import org.jetbrains.amper.frontend.ModulePart
import org.jetbrains.amper.frontend.ModuleTasksPart
import org.jetbrains.amper.frontend.RepositoriesModulePart
import org.jetbrains.amper.frontend.api.SchemaValueDelegate
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.classBasedSet
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.types.generated.fileDelegate
import org.jetbrains.amper.frontend.types.generated.passwordKeyDelegate
import org.jetbrains.amper.frontend.types.generated.usernameKeyDelegate
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.amper.stdlib.properties.readProperties
import kotlin.io.path.exists

// These converters are needed only to prevent major code changes in the [gradle-integration].
// Parts should be replaced with schema model nodes in the future.

private val defaultMavenRepositories = listOf(
    "https://repo1.maven.org/maven2",
    "https://maven.google.com",
)

// FIXME Need to get rid of this `ModulePart` convention and 
//  replace it by direct settings reading.
context(problemReporter: ProblemReporter)
fun Module.convertModuleParts(): ClassBasedSet<ModulePart<*>> {
    val parts = classBasedSet<ModulePart<*>>()

    parts += RepositoriesModulePart(
        mavenRepositories = run {
            val defaultRepositories = defaultMavenRepositories.map { url ->
                RepositoriesModulePart.Repository(
                    id = url,
                    url = url,
                    publish = false,
                    resolve = true,
                )
            }

            val customRepositories = repositories?.map { repository ->
                // FIXME Access to the file in a more safe way.
                val credPair = repository.credentials?.let { credentials ->
                    if (!credentials.file.exists()) {
                        problemReporter.reportBundleError(
                            source = credentials.fileDelegate.asBuildProblemSource(),
                            messageKey = "credentials.file.does.not.exist",
                            credentials.file.normalize(),
                            problemType = BuildProblemType.UnresolvedReference,
                        )
                        return@let null
                    } else {
                        val credentialProperties = credentials.file.readProperties()

                        fun getCredProperty(keyProperty: SchemaValueDelegate<String>): String? {
                            val property = credentialProperties.getProperty(keyProperty.value)
                            if (property == null) {
                                problemReporter.reportBundleError(
                                    source = keyProperty.asBuildProblemSource(),
                                    messageKey = "credentials.file.does.not.have.key",
                                    credentials.file.normalize(),
                                    keyProperty.value,
                                    credentialProperties.keys.joinToString(),
                                    problemType = BuildProblemType.UnresolvedReference,
                                )
                            }
                            return property
                        }

                        getCredProperty(credentials.usernameKeyDelegate) to getCredProperty(credentials.passwordKeyDelegate)
                    }
                }
                RepositoriesModulePart.Repository(
                    id = repository.id,
                    url = repository.url,
                    publish = repository.publish,
                    resolve = repository.resolve,
                    userName = credPair?.first,
                    password = credPair?.second,
                )
            } ?: emptyList()
            defaultRepositories + customRepositories
        }
    )

    parts += ModuleTasksPart(
        settings = tasks
            ?.mapValues { (_, value) -> ModuleTasksPart.TaskSettings(dependsOn = value.dependsOn?.map { it.value } ?: emptyList()) }
            ?: emptyMap(),
    )

    parts += GitSourcesModulePart(
        gitSources = sources ?: emptyList()
    )

    return parts
}
