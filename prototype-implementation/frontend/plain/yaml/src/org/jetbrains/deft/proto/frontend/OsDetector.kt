package org.jetbrains.deft.proto.frontend

interface OsDetector {

    enum class Os(val value: String) {
        macOsArm64("macos-arm64"),
        macosX64("macos-x64"),
        linuxArm64("linux-arm64"),
        linuxX64("linux-x64"),
        widnowsX64("windows-x64")
    }

    fun detect(): Os {

        if (System.getProperty("os.name").startsWith("Windows")) {
            return Os.widnowsX64
        }

        if (System.getProperty("os.name").startsWith("Mac OS X")) {
            if (System.getProperty("os.arch").startsWith("aarch64")) {
                return Os.macOsArm64
            }

            return Os.macosX64
        }

        if (System.getProperty("os.name").startsWith("Linux")) {
            if (System.getProperty("os.arch").startsWith("aarch64")) {
                return Os.linuxArm64
            }

            return Os.linuxX64
        }

        error("Couldn't detect OS")
    }
}