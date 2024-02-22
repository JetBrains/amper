/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.ClassBasedSet
import org.jetbrains.amper.frontend.Layout
import org.jetbrains.amper.frontend.MetaModulePart
import org.jetbrains.amper.frontend.ModulePart
import org.jetbrains.amper.frontend.RepositoriesModulePart
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.classBasedSet
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.Module
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.reader
import kotlin.reflect.KProperty0

// These converters are needed only to prevent major code changes in the [gradle-integration].
// Parts should be replaced with schema model nodes in the future.

context(ProblemReporterContext)
fun Module.convertModuleParts(): ClassBasedSet<ModulePart<*>> {
    val parts = classBasedSet<ModulePart<*>>()

    parts += MetaModulePart(
        layout = Layout.valueOf(module.layout.name)
    )

    parts += RepositoriesModulePart(
        mavenRepositories = repositories?.map { repository ->
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
                    val credentialProperties = Properties().apply { load(credentials.file.reader()) }

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
                userName = credPair?.first,
                password = credPair?.second,
            )
        } ?: emptyList()
    )

    return parts
}
