/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.junit.jupiter.api.parallel.ResourceLock

/**
 * This annotation is used to serialize particular test methods which potentially could write data in the same folder
 * (.konan). Writing data occurs during downloading and extracting Kotlin/Native toolchain in
 * [org.jetbrains.kotlin.gradle.utils.NativeCompilerDownloader].
 *
 * NB: Adding [kotlin.test.Test] annotation here make gutter icons disappear in IDE. For some reason, the IDE doesn't
 * look inside the annotation declaration
 */

@MustBeDocumented
@ResourceLock("KonanFolder")
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
annotation class KonanFolderLock()
