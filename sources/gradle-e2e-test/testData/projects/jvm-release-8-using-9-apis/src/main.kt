/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

fun main() {
    java.util.List.of(1, 2, 3) // List.of was added in Java 9 and should fail with jvm.release=8
}
