/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.core.properties.readProperties
import org.jetbrains.amper.frontend.ClassBasedSet
import org.jetbrains.amper.frontend.Layout
import org.jetbrains.amper.frontend.MetaModulePart
import org.jetbrains.amper.frontend.ModulePart
import org.jetbrains.amper.frontend.ModuleTasksPart
import org.jetbrains.amper.frontend.RepositoriesModulePart
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.classBasedSet
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.Module
import kotlin.io.path.exists
import kotlin.reflect.KProperty0

// These converters are needed only to prevent major code changes in the [gradle-integration].
// Parts should be replaced with schema model nodes in the future.

private val defaultMavenRepositories = listOf(
    "https://repo1.maven.org/maven2",
    "https://maven.google.com",
    "https://maven.pkg.jetbrains.space/public/p/compose/dev"
)

context(ProblemReporterContext)
fun Module.convertModuleParts(): ClassBasedSet<ModulePart<*>> {
    val parts = classBasedSet<ModulePart<*>>()

    parts += MetaModulePart(
        layout = Layout.valueOf(module.layout.name)
    )

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
                        SchemaBundle.reportBundleError(
                            property = credentials::file,
                            messageKey = "credentials.file.does.not.exist",
                            credentials.file.normalize()
                        )
                        return@let null
                    } else {
                        val credentialProperties = credentials.file.readProperties()

                        fun getCredProperty(keyProperty: KProperty0<String>): String? =
                            credentialProperties.getProperty(keyProperty.get())
                                ?: SchemaBundle.reportBundleError(
                                    property = keyProperty,
                                    messageKey = "credentials.file.does.not.have.key",
                                    credentials.file.normalize(),
                                    keyProperty.get(),
                                    credentialProperties.keys.joinToString(),
                                )

                        getCredProperty(credentials::usernameKey) to getCredProperty(credentials::passwordKey)
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

    return parts
}
