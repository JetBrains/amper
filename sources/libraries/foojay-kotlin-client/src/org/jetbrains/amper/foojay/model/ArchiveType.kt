/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.foojay.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The type of archive that JDK is packaged in.
 */
@Serializable
enum class ArchiveType(val apiValue: String) {
    @SerialName("apk")
    APK("apk"),
    @SerialName("bin")
    BIN("bin"),
    @SerialName("cab")
    CAB("cab"),
    @SerialName("deb")
    DEB("deb"),
    @SerialName("dmg")
    DMG("dmg"),
    @SerialName("msi")
    MSI("msi"),
    @SerialName("pkg")
    PKG("pkg"),
    @SerialName("rpm")
    RPM("rpm"),
    @SerialName("src_tar")
    SRC_TAR("src_tar"),
    @SerialName("tar")
    TAR("tar"),
    @SerialName("tar.gz")
    TAR_GZ("tar.gz"),
    @SerialName("tar.xz")
    TAR_XZ("tar.xz"),
    @SerialName("tgz")
    TGZ("tgz"),
    @SerialName("tar.z")
    TAR_Z("tar.z"),
    @SerialName("zip")
    ZIP("zip"),
    @SerialName("exe")
    EXE("exe"),
}
