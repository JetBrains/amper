package org.jetbrains.deft.proto.frontend

import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsExec
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

data class KotlinPart(
    val languageVersion: String?,
    val apiVersion: String?,
    val sdkVersion: String?,
    val progressiveMode: Boolean?,
    val languageFeatures: List<String>,
    val optIns: List<String>,
    val freeCompilerArgs: List<String> = emptyList(),
    val allWarningsAsErrors: Boolean? = null,
    val suppressWarnings: Boolean? = null,
    val verbose: Boolean? = null,
) : FragmentPart<KotlinPart> {
    override fun propagate(parent: KotlinPart): FragmentPart<KotlinPart> =
        KotlinPart(
            parent.languageVersion ?: languageVersion,
            parent.apiVersion ?: apiVersion,
            parent.sdkVersion ?: sdkVersion,
            parent.progressiveMode ?: progressiveMode,
            languageFeatures.ifEmpty { parent.languageFeatures },
            optIns.ifEmpty { parent.optIns },
            (freeCompilerArgs + parent.freeCompilerArgs), // TODO check

            // Inherit parent state if no current state is set.
            allWarningsAsErrors ?: true && parent.allWarningsAsErrors ?: false,
            suppressWarnings ?: true || parent.suppressWarnings ?: false,
            verbose ?: true || parent.verbose ?: false,
        )

    override fun default(): FragmentPart<*> {
        return KotlinPart(
            languageVersion ?: "1.8",
            apiVersion ?: languageVersion,
            sdkVersion,
            progressiveMode ?: false,
            languageFeatures.takeIf { it.isNotEmpty() } ?: listOf(),
            optIns.takeIf { it.isNotEmpty() } ?: listOf(),
        )
    }
}

data class TestPart(val junitPlatform: Boolean?) : FragmentPart<TestPart> {
    override fun propagate(parent: TestPart): FragmentPart<*> =
        TestPart(parent.junitPlatform ?: junitPlatform)

    override fun default(): FragmentPart<*> = TestPart(junitPlatform ?: true)
}

data class AndroidPart(
    val compileSdkVersion: String?,
    val minSdkVersion: Int?,
    val sourceCompatibility: String?,
    val targetCompatibility: String?,
    val jvmTarget: String? = null,
    val publishLibraryVariants: Boolean = false,
    val publishLibraryVariantsGroupedByFlavor: Boolean = false,
    val moduleName: String? = null,
    val noJdk: Boolean = false,
) : FragmentPart<AndroidPart> {
    override fun default(): FragmentPart<AndroidPart> =
        AndroidPart(
            compileSdkVersion ?: "android-33",
            minSdkVersion ?: 21,
            sourceCompatibility ?: "17",
            targetCompatibility ?: "17",
            jvmTarget ?: "17",

        )
}

data class JvmPart(
    val mainClass: String?,
    val packagePrefix: String?,
    val jvmTarget: String?,
    val moduleName: String? = null,
    val noJdk: Boolean = false,
) : FragmentPart<JvmPart> {
    override fun default(): FragmentPart<JvmPart> =
        JvmPart(
            mainClass ?: "MainKt",
            packagePrefix ?: "",
            jvmTarget ?: "17",
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
    override fun default() = JsPart(Browser())
}

data class NativeApplicationPart(
    val entryPoint: String?,
    val baseName: String? = null,
    // Do not touch defaults of KMPP.
    val debuggable: Boolean? = null,
    // Do not touch defaults of KMPP.
    val optimized: Boolean? = null,
    val binaryOptions: Map<String, String> = emptyMap(),
    val linkerOpts: List<String> = emptyList(),
) : FragmentPart<NativeApplicationPart> {
    override fun default(): FragmentPart<NativeApplicationPart> =
        NativeApplicationPart(entryPoint ?: "main")
}

data class PublicationPart(
    val group: String?,
    val version: String?,
) : FragmentPart<PublicationPart> {
    override fun default(): FragmentPart<PublicationPart> =
        PublicationPart(group ?: "org.example", version ?: "SNAPSHOT-1.0")
}