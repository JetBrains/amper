/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.amper.plugins.*
import javax.lang.model.type.TypeMirror
import java.net.http.HttpClient

class MyClass

@TaskAction
fun task() {
    val typeMirrorClass = TypeMirror::class.java  // from platform classloader
    val httpClientClass = HttpClient::class.java  // from platform classloader
    val classLoader = MyClass::class.java.classLoader
    if (classLoader !== Thread.currentThread().contextClassLoader) {
        error("ClassLoader mismatch!")
    }
    val amperInternals = try {
        classLoader.loadClass("org.jetbrains.amper.tasks.custom.TaskFromPlugin")
        error("Amper internals should not be acccessible!")
    } catch (e: ClassNotFoundException) {
        // Expected!
    }
    println("Everything is in order")
}
