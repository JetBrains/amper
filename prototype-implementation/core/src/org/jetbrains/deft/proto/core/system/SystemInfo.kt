package org.jetbrains.deft.proto.core.system

interface SystemInfo {

    enum class OsFamily(val value: String) {
        Windows("windows"),
        Linux("linux"),
        MacOs("macos"),
        FreeBSD("freebsd"),
        Solaris("sunos");

        private val isUnix: Boolean
            get() = this != Windows

        val isFileSystemCaseSensitive: Boolean get() = isUnix && this != MacOs
    }

    enum class Arch(val value: String) {
        X64("x64"),
        Arm64("arm64")
    }

    data class Os(val family: OsFamily, val version: String, val arch: Arch) {
        val familyArch get() = "${family.value.lowercase()}-${arch.value.lowercase()}"
    }

    fun detect(): Os {
        val osName = System.getProperty("os.name")
        val osNameLowerCased = osName.lowercase()
        val osFamily =
            if (osNameLowerCased.startsWith("windows")) OsFamily.Windows
            else if (osNameLowerCased.startsWith("mac")) OsFamily.MacOs
            else if (osNameLowerCased.startsWith("linux")) OsFamily.Linux
            else if (osNameLowerCased.startsWith("freebsd")) OsFamily.FreeBSD
            else if (osNameLowerCased.startsWith("sunos")) OsFamily.Solaris
            else error("Could not determine OS family")

        var version = System.getProperty("os.version").lowercase()

        if (osName.startsWith("Windows") && osName.matches("Windows \\d+".toRegex())) {
            // for whatever reason, JRE reports "Windows 11" as a name and "10.0" as a version on Windows 11
            try {
                val version2 = osName.substring("Windows".length + 1) + ".0"
                if (version2.toFloat() > version.toFloat()) {
                    version = version2
                }
            } catch (ignored: NumberFormatException) {
            }
        }

        val rawArch = System.getProperty("os.arch").lowercase()
        val arch = if (rawArch.startsWith("aarch64") || rawArch.startsWith("amd64"))
            Arch.Arm64
        else
            Arch.X64

        return Os(osFamily, version, arch)
    }
}

object DefaultSystemInfo: SystemInfo