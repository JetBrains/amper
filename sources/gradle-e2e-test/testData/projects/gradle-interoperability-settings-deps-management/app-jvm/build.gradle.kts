/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    kotlin("jvm") //version "1.9.20"
    application
}

dependencies {
    implementation(project(":lib-multiplatform"))
}
