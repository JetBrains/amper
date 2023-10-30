/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

data class KotlinPart(
    val languageVersion: String?,
    val apiVersion: String?,
    val allWarningsAsErrors: Boolean? = null,
    val freeCompilerArgs: List<String> = emptyList(),
    val suppressWarnings: Boolean? = null,
    val verbose: Boolean? = null,
    val linkerOpts: List<String> = emptyList(),
    val debug: Boolean? = null,
    val progressiveMode: Boolean?,
    val languageFeatures: List<String>,
    val optIns: List<String>,
    val serialization: String?
) : FragmentPart<KotlinPart> {
    override fun propagate(parent: KotlinPart): FragmentPart<KotlinPart> =
        KotlinPart(
            languageVersion ?: parent.languageVersion,
            apiVersion ?: parent.apiVersion,
            allWarningsAsErrors ?: true && parent.allWarningsAsErrors ?: false,
            (freeCompilerArgs + parent.freeCompilerArgs),
            suppressWarnings ?: true || parent.suppressWarnings ?: false,
            verbose ?: true || parent.verbose ?: false, // TODO check

            // Inherit parent state if no current state is set.
            linkerOpts.ifEmpty { parent.linkerOpts },
            debug ?: parent.debug,
            parent.progressiveMode ?: progressiveMode,
            languageFeatures.ifEmpty { parent.languageFeatures },
            optIns.ifEmpty { parent.optIns },
            serialization ?: parent.serialization
        )

    override fun default(module: PotatoModule): FragmentPart<*> {
        return copy(
            languageVersion = languageVersion ?: "1.9",
            apiVersion = apiVersion ?: languageVersion,
            progressiveMode = progressiveMode ?: false,
        )
    }
}

data class AndroidPart(
    val compileSdk: String?,
    val minSdk: String? = null,
    val maxSdk: Int? = null,
    val targetSdk: String? = null,
    val applicationId: String? = null,
    val namespace: String? = null,
) : FragmentPart<AndroidPart> {

    override fun propagate(parent: AndroidPart): AndroidPart {
            return AndroidPart(
                compileSdk = parent.compileSdk ?: this.compileSdk,
                minSdk = parent.minSdk ?: this.minSdk,
                maxSdk = parent.maxSdk ?: this.maxSdk,
                targetSdk = parent.targetSdk ?: this.targetSdk,
                applicationId = parent.applicationId ?: this.applicationId,
                namespace = parent.namespace ?: this.namespace,
            )
    }
    override fun default(module: PotatoModule): FragmentPart<AndroidPart> =
        AndroidPart(
            targetSdk = targetSdk ?: "34",
            compileSdk = compileSdk ?: "android-34",
            minSdk = minSdk ?: "21",
            namespace = namespace?: "com.example.${module.userReadableName.prepareToNamespace()}"
        )
}

data class JvmPart(
    val mainClass: String? = null,
    val target: String? = null,
) : FragmentPart<JvmPart> {
    override fun propagate(parent: JvmPart) = JvmPart(
        mainClass ?: parent.mainClass,
        target ?: parent.target
    )

    override fun default(module: PotatoModule): FragmentPart<JvmPart> =
        JvmPart(
            mainClass,
            target ?: "17",
        )
}

enum class JUnitVersion(val key: String) {
    JUNIT4("junit-4"), JUNIT5("junit-5"), NONE("none");

    companion object Index : EnumMap<JUnitVersion, String>(JUnitVersion::values, JUnitVersion::key, JUnitVersion::class)
}

data class JUnitPart(
    val version: JUnitVersion? = null
) : FragmentPart<JUnitPart> {
    override fun propagate(parent: JUnitPart) = JUnitPart(
        version ?: parent.version
    )

    override fun default(module: PotatoModule) = JUnitPart(
        version ?: JUnitVersion.JUNIT4
    )
}

data class JavaPart(
    val source: String?,
) : FragmentPart<JavaPart>

data class NativeApplicationPart(
    val entryPoint: String?,
    val baseName: String? = null,
    // Do not touch defaults of KMPP.
    val debuggable: Boolean? = null,
    // Do not touch defaults of KMPP.
    val optimized: Boolean? = null,
    val binaryOptions: Map<String, String> = emptyMap(),
    val declaredFrameworkBasename: String? = null,
    val frameworkParams: Map<String, String>? = null,
) : FragmentPart<NativeApplicationPart> {
    override fun propagate(parent: NativeApplicationPart): FragmentPart<*> {
        return NativeApplicationPart(
            entryPoint = entryPoint ?: parent.entryPoint,
            declaredFrameworkBasename = declaredFrameworkBasename ?: parent.declaredFrameworkBasename,
            frameworkParams = frameworkParams ?: parent.frameworkParams,
        )
    }

    override fun default(module: PotatoModule): FragmentPart<NativeApplicationPart> =
        NativeApplicationPart(
            entryPoint = entryPoint,
            baseName = "kotlin",
            declaredFrameworkBasename = declaredFrameworkBasename,
            frameworkParams = frameworkParams,
        )
}

data class PublicationPart(
    val group: String?,
    val version: String?,
) : FragmentPart<PublicationPart> {
    override fun propagate(parent: PublicationPart) = PublicationPart(
        group ?: parent.group,
        version ?: parent.version
    )

    override fun default(module: PotatoModule): FragmentPart<PublicationPart> =
        PublicationPart(group ?: "org.example", version ?: "SNAPSHOT-1.0")
}

data class ComposePart(val enabled: Boolean?) : FragmentPart<ComposePart> {
    override fun propagate(parent: ComposePart): FragmentPart<*> {
        return ComposePart(enabled ?: parent.enabled)
    }

    override fun default(module: PotatoModule): FragmentPart<*> {
        return ComposePart(enabled ?: false)
    }

}

data class IosPart(val teamId: String?) : FragmentPart<IosPart> {
    override fun propagate(parent: IosPart): FragmentPart<*> {
        return IosPart(teamId ?: parent.teamId)
    }

    override fun default(module: PotatoModule): FragmentPart<*> {
        return IosPart(teamId)
    }
}