package org.jetbrains.deft.proto.frontend.toolchain

interface AndroidKotlinFragment : KotlinFragment {
    val compileSdkVersion : String // default: android-31
}