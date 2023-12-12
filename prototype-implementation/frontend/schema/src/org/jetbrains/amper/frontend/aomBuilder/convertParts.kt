/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.frontend.AndroidPart
import org.jetbrains.amper.frontend.ComposePart
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
import org.jetbrains.amper.frontend.NativeApplicationPart
import org.jetbrains.amper.frontend.PublicationPart
import org.jetbrains.amper.frontend.RepositoriesModulePart
import org.jetbrains.amper.frontend.buildClassBasedSet
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Settings
import java.util.*
import kotlin.io.path.reader


// These converters are needed only to prevent major code changes in the [gradle-integration].
// Parts should be replaced with schema model nodes in the future.

fun Settings.convertFragmentParts() = buildClassBasedSet parts@{
    this += KotlinPart(
        languageVersion = kotlin.value.languageVersion.value,
        apiVersion = kotlin.value.apiVersion.value,
        allWarningsAsErrors = kotlin.value.allWarningsAsErrors.value,
        freeCompilerArgs = kotlin.value.freeCompilerArgs.value,
        suppressWarnings = kotlin.value.suppressWarnings.value,
        verbose = kotlin.value.verbose.value,
        linkerOpts = kotlin.value.linkerOpts.value,
        debug = kotlin.value.debug.value,
        progressiveMode = kotlin.value.progressiveMode.value,
        languageFeatures = kotlin.value.languageFeatures.value,
        optIns = kotlin.value.optIns.value,
        serialization = kotlin.value.serialization.value?.engine?.value,
    )

    android.value?.let {
        this@parts += AndroidPart(
            compileSdk = it.compileSdk.value,
            minSdk = it.minSdk.value,
            maxSdk = it.maxSdk.value.toIntOrNull(), // TODO Verify
            targetSdk = it.targetSdk.value,
            applicationId = it.applicationId.value,
            namespace = it.namespace.value,
        )
    }

    this += JvmPart(
        jvm.value.mainClass.value,
        jvm.value.target.value,
    )

    junit.value?.let {
        this@parts += JUnitPart(JUnitVersion[it]) // TODO Replace by enum.
    }

    java.value?.let {
        this@parts += JavaPart(it.source.value)
    }

    ios.value?.let {
        this@parts += NativeApplicationPart(
            // TODO Fill all other options.
            entryPoint = null, // TODO Pass entry point.
            declaredFrameworkBasename = it.framework.value?.basename?.value,
            frameworkParams = it.framework.value?.mappings?.value
        )

        this@parts += IosPart(it.teamId.value)
    }

    publishing.value?.let {
        this@parts += PublicationPart(
            group = it.group.value,
            version = it.version.value
        )
    }

    compose.value?.let {
        this@parts += ComposePart(it.enabled.value)
    }

    kover.value?.let {
        this@parts += KoverPart(
            enabled = it.enabled.value,
            xml = it.xml.value?.let {
                KoverXmlPart(it.onCheck.value, it.reportFile.value)
            },
            html = it.html.value?.let {
                KoverHtmlPart(it.title.value, it.charset.value, it.onCheck.value, it.reportDir.value)
            }
        )
    }
}

fun Module.convertModuleParts() = buildClassBasedSet parts@{
    this@parts += MetaModulePart(
        layout = Layout.valueOf(module.value.layout.value.name)
    )

    this@parts += RepositoriesModulePart(
        mavenRepositories = repositories.value.map {
            // FIXME Access to the file in a more safe way.
            val credPair = it.credentials.value?.let {
                // TODO Report missing property and file.
                val credentialProperties = Properties().apply { load(it.file.value.reader()) }
                fun getCredProperty(key: String): String = credentialProperties.getProperty(key)
                    ?: run { error("No such key: $key") }
                getCredProperty(it.usernameKey.value) to getCredProperty(it.passwordKey.value)
            }
            RepositoriesModulePart.Repository(
                id = it.id.value,
                url = it.url.value,
                publish = it.publish.value,
                userName = credPair?.first,
                password = credPair?.second,
            )
        }
    )
}