val iosTarget: (String, KotlinNativeTarget.() -> Unit) -> KotlinNativeTarget =
    when {
    System.getenv("SDK_NAME")?.startsWith("iphoneos") == true -> ::iosArm64
    System.getenv("NATIVE_ARCH")?.startsWith("arm") == true -> ::iosSimulatorArm64
    else -> ::iosX64
}


fun main(args: Array<String>) {
    iosTarget("aaa") {}
}

