package org.jetbrains.deft.proto.frontend


@Suppress("unused")
enum class KotlinVersion(private val version: String) {
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
    companion object Index : EnumMap<KotlinVersion>(KotlinVersion::values, KotlinVersion::name)
}

data class KotlinPartBuilder(
    var languageVersion: KotlinVersion? = null,
    var apiVersion: KotlinVersion? = null,
    var sdkVersion: String? = null,
    var progressiveMode: Boolean? = null,
    val languageFeatures: MutableList<String> = mutableListOf(),
    val optIns: MutableList<String> = mutableListOf(),
    var jvmTarget: Int? = null,
) {
    companion object : BuilderCompanion<KotlinPartBuilder>(::KotlinPartBuilder)
}

data class AndroidPartBuilder(
    var compileSdkVersion: String? = null,
    var androidMinSdkVersion: Int? = null,
    var sourceCompatibility: String? = null,
    var targetCompatibility: String? = null,
) {
    companion object : BuilderCompanion<AndroidPartBuilder>(::AndroidPartBuilder)
}

data class JavaPartBuilder(
    var mainClass: String? = null,
    var packagePrefix: String? = null,
    var jvmTarget: String? = null,
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