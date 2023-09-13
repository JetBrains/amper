package org.jetbrains.deft.proto.frontend

import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsExec
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

data class KotlinPart(
    val languageVersion: String?,
    val apiVersion: String?,
    val allWarningsAsErrors: Boolean? = null,
    val freeCompilerArgs: List<String> = emptyList(),
    val suppressWarnings: Boolean? = null,
    val verbose: Boolean? = null,
    val linkerOpts: List<String> = emptyList(),
    val debug: Boolean?,
    val progressiveMode: Boolean?,
    val languageFeatures: List<String>,
    val optIns: List<String>,
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
        )

    override fun default(module: PotatoModule): FragmentPart<*> {
        return KotlinPart(
            languageVersion = languageVersion ?: "1.9",
            apiVersion = apiVersion ?: languageVersion,
            debug = null,
            progressiveMode = progressiveMode ?: false,
            languageFeatures = languageFeatures.takeIf { it.isNotEmpty() } ?: listOf(),
            optIns = optIns.takeIf { it.isNotEmpty() } ?: listOf(),
            linkerOpts = emptyList(),
        )
    }
}

data class TestPart(val junitPlatform: Boolean?) : FragmentPart<TestPart> {
    override fun propagate(parent: TestPart): FragmentPart<*> =
        TestPart(junitPlatform ?: parent.junitPlatform)

    override fun default(module: PotatoModule): FragmentPart<*> = TestPart(junitPlatform ?: true)
}

data class AndroidPart(
    val compileSdkVersion: String?,
    val minSdk: String? = null,
    val minSdkPreview: String? = null,
    val maxSdk: Int? = null,
    val targetSdk: String? = null,
    val applicationId: String? = null,
    val namespace: String? = null,
) : FragmentPart<AndroidPart> {
    override fun default(module: PotatoModule): FragmentPart<AndroidPart> =
        AndroidPart(
            compileSdkVersion = compileSdkVersion ?: "android-34",
            minSdk = minSdk ?: "21",
            namespace = "com.example.${module.userReadableName.prepareToNamespace()}"
        )
}

data class JavaPart(
    val mainClass: String?,
    val packagePrefix: String?,
    val target: String?,
    val source: String?,
    val moduleName: String? = null,
) : FragmentPart<JavaPart> {
    override fun default(module: PotatoModule): FragmentPart<JavaPart> =
        JavaPart(
            mainClass,
            packagePrefix ?: "",
            target ?: "17",
            source ?: target ?: "17",
        )
}

data class JsPart(
    val mode: Mode,
    val outputModuleName: String? = null,
) : FragmentPart<JsPart> {
    sealed interface Mode
    data class Browser(
        val webpackConfig: KotlinWebpackConfig.() -> Unit = {}
    ) : Mode

    data class NodeJs(
        val runTask: NodeJsExec.() -> Unit = {}
    ) : Mode

    override fun default(module: PotatoModule) = JsPart(Browser())
}

data class NativeApplicationPart(
    val entryPoint: String?,
    val baseName: String? = null,
    // Do not touch defaults of KMPP.
    val debuggable: Boolean? = null,
    // Do not touch defaults of KMPP.
    val optimized: Boolean? = null,
    val binaryOptions: Map<String, String> = emptyMap(),
) : FragmentPart<NativeApplicationPart> {
    override fun default(module: PotatoModule): FragmentPart<NativeApplicationPart> =
        NativeApplicationPart(entryPoint, "kotlin")
}

data class PublicationPart(
    val group: String?,
    val version: String?,
) : FragmentPart<PublicationPart> {
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