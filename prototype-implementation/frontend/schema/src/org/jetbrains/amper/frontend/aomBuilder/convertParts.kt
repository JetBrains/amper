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
import org.jetbrains.amper.frontend.JUnitVersion
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
import org.jetbrains.amper.frontend.classBasedSet
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Settings
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.reader


// These converters are needed only to prevent major code changes in the [gradle-integration].
// Parts should be replaced with schema model nodes in the future.

fun Settings?.convertFragmentParts(): ClassBasedSet<FragmentPart<*>> {
    val parts = classBasedSet<FragmentPart<*>>()

    parts += KotlinPart(
        languageVersion = this?.kotlin?.value?.languageVersion?.value,
        apiVersion = this?.kotlin?.value?.apiVersion?.value,
        allWarningsAsErrors = this?.kotlin?.value?.allWarningsAsErrors?.value,
        freeCompilerArgs = this?.kotlin?.value?.freeCompilerArgs?.value ?: emptyList(),
        suppressWarnings = this?.kotlin?.value?.suppressWarnings?.value,
        verbose = this?.kotlin?.value?.verbose?.value,
        linkerOpts = this?.kotlin?.value?.linkerOpts?.value ?: emptyList(),
        debug = this?.kotlin?.value?.debug?.value,
        progressiveMode = this?.kotlin?.value?.progressiveMode?.value,
        languageFeatures = this?.kotlin?.value?.languageFeatures?.value ?: emptyList(),
        optIns = this?.kotlin?.value?.optIns?.value ?: emptyList(),
        serialization = this?.kotlin?.value?.serialization?.value?.engine?.value,
    )

    parts += AndroidPart(
        // TODO Replace with enum for Android versions.
        compileSdk = this?.android?.value?.compileSdk?.value?.let { "android-$it" },
        minSdk = this?.android?.value?.minSdk?.value,
        maxSdk = this?.android?.value?.maxSdk?.value?.toIntOrNull(), // TODO Verify
        targetSdk = this?.android?.value?.targetSdk?.value,
        applicationId = this?.android?.value?.applicationId?.value,
        namespace = this?.android?.value?.namespace?.value,
    )

    parts += IosPart(this?.ios?.value?.teamId?.value)

    parts += JavaPart(this?.java?.value?.source?.value)

    parts += JvmPart(
        this?.jvm?.value?.mainClass?.value,
        this?.jvm?.value?.target?.value,
    )

    parts += JUnitPart(this?.junit?.value?.let { JUnitVersion[it] }) // TODO Replace by enum.

    parts += PublicationPart(
        group = this?.publishing?.value?.group?.value,
        version = this?.publishing?.value?.version?.value
    )

    parts += NativeApplicationPart(
        // TODO Fill all other options.
        entryPoint = null, // TODO Pass entry point.
        declaredFrameworkBasename = this?.ios?.value?.framework?.value?.basename?.value,
        frameworkParams = this?.ios?.value?.framework?.value?.mappings?.value
    )

    parts += ComposePart(this?.compose?.value?.enabled?.value)

    parts += KoverPart(
        enabled = this?.kover?.value?.enabled?.value,
        xml = this?.kover?.value?.xml?.value?.let {
            KoverXmlPart(it.onCheck.value, it.reportFile.value)
        },
        html = this?.kover?.value?.html?.value?.let {
            KoverHtmlPart(it.title.value, it.charset.value, it.onCheck.value, it.reportDir.value)
        }
    )
    return parts
}

context(ProblemReporterContext)
fun Module.convertModuleParts(): ClassBasedSet<ModulePart<*>> {
    val parts = classBasedSet<ModulePart<*>>()

    parts += MetaModulePart(
        layout = Layout.valueOf(module.value.layout.value.name)
    )

    parts += RepositoriesModulePart(
        mavenRepositories = repositories.value?.map {
            // FIXME Access to the file in a more safe way.
            val credPair = it.credentials.value?.let {
                if (!it.file.value.exists()) {
                    SchemaBundle.reportBundleError(it.file, "credentials.file.does.not.exist", it.file.value.normalize())
                    return@let null
                } else {
                    val credentialProperties = Properties().apply { load(it.file.value.reader()) }
                    // TODO Report missing file.
                    fun getCredProperty(key: String): String = credentialProperties.getProperty(key)
                        ?: run { error("No such key: $key") }
                    getCredProperty(it.usernameKey.value) to getCredProperty(it.passwordKey.value)
                }
            }
            RepositoriesModulePart.Repository(
                id = it.id.value,
                url = it.url.value,
                publish = it.publish.value,
                userName = credPair?.first,
                password = credPair?.second,
            )
        } ?: emptyList()
    )

    return parts
}