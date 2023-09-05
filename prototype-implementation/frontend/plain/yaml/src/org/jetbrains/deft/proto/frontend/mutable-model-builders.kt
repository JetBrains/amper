package org.jetbrains.deft.proto.frontend


@Suppress("unused")
enum class KotlinVersion(internal val version: String) {
    Kotlin20("2.0"),
    Kotlin19("1.9"),
    Kotlin18("1.8"),
    Kotlin17("1.7"),
    Kotlin16("1.6"),
    Kotlin15("1.5"),
    Kotlin14("1.4"),
    Kotlin13("1.3"),
    Kotlin12("1.2"),
    Kotlin11("1.1"),
    Kotlin10("1.0");

    override fun toString(): String = version
    companion object Index : EnumMap<KotlinVersion, String>(
        KotlinVersion::values,
        KotlinVersion::version,
        KotlinVersion::class
    )
}

data class KotlinPartBuilder(
    var languageVersion: KotlinVersion? = null,
    var apiVersion: KotlinVersion? = null,
    var allWarningsAsErrors: Boolean? = null,
    val freeCompilerArgs: MutableList<String> = mutableListOf(),
    var suppressWarnings: Boolean? = null,
    var verbose: Boolean? = null,
    val likerOpts: MutableList<String> = mutableListOf(),
    var debug: Boolean? = null,
    var progressiveMode: Boolean? = null,
    val languageFeatures: MutableList<String> = mutableListOf(),
    var optIns: MutableList<String> = mutableListOf(),
) {
    companion object : BuilderCompanion<KotlinPartBuilder>(::KotlinPartBuilder)
}

data class AndroidPartBuilder(
    var compileSdkVersion: String? = null,
    var minSdk: String? = null,
    var minSdkPreview: String? = null,
    var maxSdk: Int? = null,
    var targetSdk: String? = null,
    var applicationId: String? = null,
    var namespace: String? = null,
) {
    companion object : BuilderCompanion<AndroidPartBuilder>(::AndroidPartBuilder)
}

data class JavaPartBuilder(
    var mainClass: String? = null,
    var packagePrefix: String? = null,
    var target: String? = null,
    var source: String? = null,
) {
    companion object : BuilderCompanion<JavaPartBuilder>(::JavaPartBuilder)
}

data class PublishingPartBuilder(
    var group: String? = null,
    var version: String? = null,
) {
    companion object : BuilderCompanion<PublishingPartBuilder>(::PublishingPartBuilder)
}

data class NativePartBuilder(
    var entryPoint: String? = null,
) {
    companion object : BuilderCompanion<NativePartBuilder>(::NativePartBuilder)
}

data class JunitPartBuilder(
    var platformEnabled: Boolean? = null,
) {
    companion object : BuilderCompanion<JunitPartBuilder>(::JunitPartBuilder)
}

data class ComposePartBuilder(var enabled: Boolean? = null) {
    companion object: BuilderCompanion<ComposePartBuilder>(::ComposePartBuilder)
}