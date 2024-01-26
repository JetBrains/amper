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
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.classBasedSet
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.Module
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.reader

// These converters are needed only to prevent major code changes in the [gradle-integration].
// Parts should be replaced with schema model nodes in the future.

context(ProblemReporterContext)
fun Module.convertModuleParts(): ClassBasedSet<ModulePart<*>> {
    val parts = classBasedSet<ModulePart<*>>()

    parts += MetaModulePart(
        layout = Layout.valueOf(module.layout.name)
    )

    parts += RepositoriesModulePart(
        mavenRepositories = repositories?.map {
            // FIXME Access to the file in a more safe way.
            val credPair = it.credentials?.let {
                if (!it.file.exists()) {
                    SchemaBundle.reportBundleError(
                        it::file.valueBase,
                        "credentials.file.does.not.exist",
                        it.file.normalize()
                    )
                    return@let null
                } else {
                    val credentialProperties = Properties().apply { load(it.file.reader()) }
                    // TODO Report missing file.
                    fun getCredProperty(key: String): String = credentialProperties.getProperty(key)
                        ?: run { error("No such key: $key") }
                    getCredProperty(it.usernameKey) to getCredProperty(it.passwordKey)
                }
            }
            RepositoriesModulePart.Repository(
                id = it.id,
                url = it.url,
                publish = it.publish,
                userName = credPair?.first,
                password = credPair?.second,
            )
        } ?: emptyList()
    )

    return parts
}
