/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.AndroidPart
import org.jetbrains.amper.frontend.ClassBasedSet
import org.jetbrains.amper.frontend.ComposePart
import org.jetbrains.amper.frontend.FragmentPart
import org.jetbrains.amper.frontend.IosPart
import org.jetbrains.amper.frontend.JUnitPart
import org.jetbrains.amper.frontend.JavaPart
import org.jetbrains.amper.frontend.JvmPart
import org.jetbrains.amper.frontend.KotlinPart
import org.jetbrains.amper.frontend.KoverHtmlPart
import org.jetbrains.amper.frontend.KoverPart
import org.jetbrains.amper.frontend.KoverXmlPart
import org.jetbrains.amper.frontend.Layout
import org.jetbrains.amper.frontend.MetaModulePart
import org.jetbrains.amper.frontend.ModulePart
import org.jetbrains.amper.frontend.NativeApplicationPart
import org.jetbrains.amper.frontend.PublicationPart
import org.jetbrains.amper.frontend.RepositoriesModulePart
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.classBasedSet
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.JUnitVersion
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Settings
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.reader


// These converters are needed only to prevent major code changes in the [gradle-integration].
// Parts should be replaced with schema model nodes in the future.

fun Settings?.convertFragmentParts(): ClassBasedSet<FragmentPart<*>> {
    val parts = classBasedSet<FragmentPart<*>>()

    parts += KotlinPart(
        languageVersion = this?.kotlin?.languageVersion?.schemaValue,
        apiVersion = this?.kotlin?.apiVersion?.schemaValue,
        allWarningsAsErrors = this?.kotlin?.allWarningsAsErrors,
        freeCompilerArgs = this?.kotlin?.freeCompilerArgs ?: emptyList(),
        suppressWarnings = this?.kotlin?.suppressWarnings,
        verbose = this?.kotlin?.verbose,
        linkerOpts = this?.kotlin?.linkerOpts ?: emptyList(),
        debug = this?.kotlin?.debug,
        progressiveMode = this?.kotlin?.progressiveMode,
        languageFeatures = this?.kotlin?.languageFeatures ?: emptyList(),
        optIns = this?.kotlin?.optIns ?: emptyList(),
        serialization = this?.kotlin?.serialization?.format,
    )

    parts += AndroidPart(
        // TODO Replace with enum for Android versions.
        compileSdk = this?.android?.compileSdk?.withPrefix,
        minSdk = this?.android?.minSdk?.schemaValue,
        maxSdk = this?.android?.maxSdk?.versionNumber,
        targetSdk = this?.android?.targetSdk?.schemaValue,
        applicationId = this?.android?.applicationId,
        namespace = this?.android?.namespace,
    )

    parts += IosPart(this?.ios?.teamId)

    parts += JavaPart(this?.java?.source?.schemaValue)

    parts += JvmPart(
        this?.jvm?.mainClass,
        this?.jvm?.target?.schemaValue,
    )

    parts += JUnitPart(this?.junit?.let { JUnitVersion.valueOf(it.name) })

    parts += PublicationPart(
        group = this?.publishing?.group,
        version = this?.publishing?.version
    )

    parts += NativeApplicationPart(
        entryPoint = this?.native?.entryPoint,
        declaredFrameworkBasename = this?.ios?.framework?.basename,
    )

    parts += ComposePart(this?.compose?.enabled)

    parts += KoverPart(
        enabled = this?.kover?.enabled,
        xml = this?.kover?.xml?.let {
            KoverXmlPart(it.onCheck, it.reportFile?.pathString)
        },
        html = this?.kover?.html?.let {
            KoverHtmlPart(it.title, it.charset, it.onCheck, it.reportDir?.pathString)
        }
    )
    return parts
}

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
