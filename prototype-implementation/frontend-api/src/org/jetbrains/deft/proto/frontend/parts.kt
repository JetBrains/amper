package org.jetbrains.deft.proto.frontend

data class KotlinPart(
    val languageVersion: String?,
    val apiVersion: String?,
    val progressiveMode: Boolean?,
    val languageFeatures: List<String>,
    val optIns: List<String>,
) : FragmentPart<KotlinPart> {
    override fun propagate(parent: KotlinPart): FragmentPart<KotlinPart> =
        KotlinPart(
            parent.languageVersion ?: languageVersion,
            parent.apiVersion ?: apiVersion,
            parent.progressiveMode ?: progressiveMode,
            languageFeatures.ifEmpty { parent.languageFeatures },
            optIns.ifEmpty { parent.optIns },
        )

    override fun default(): FragmentPart<*> {
        return KotlinPart(
            languageVersion ?: "1.8",
            apiVersion ?: languageVersion,
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
) : FragmentPart<AndroidPart> {
    override fun default(): FragmentPart<AndroidPart> =
        AndroidPart(
            compileSdkVersion ?: "android-33",
            minSdkVersion ?: 21,
            sourceCompatibility ?: "17",
            targetCompatibility ?: "17",
        )
}

data class JavaPart(
    val mainClass: String?,
    val packagePrefix: String?,
    val jvmTarget: String?,
) : FragmentPart<JavaPart> {
    override fun default(): FragmentPart<JavaPart> =
        JavaPart(mainClass ?: "MainKt", packagePrefix ?: "", jvmTarget ?: "17")
}

data class NativeApplicationPart(
    val entryPoint: String?
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