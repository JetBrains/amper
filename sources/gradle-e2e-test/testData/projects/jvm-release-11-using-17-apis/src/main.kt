/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import java.util.List
import java.time.InstantSource

fun main() {
    List.of(1, 2, 3) // List.of was added in Java 9 and should be allowed with jvm.release=11
    InstantSource.system() // available since Java 17, should NOT be allowed with jvm.release=11
}
