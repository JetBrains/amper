/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test

import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.core.system.Arch
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.test.TestCollector
import org.jetbrains.amper.test.TestCollector.Companion.runTestWithCollector
import org.jetbrains.amper.test.TestUtil
import org.junit.jupiter.api.Disabled
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmperKspTest : AmperIntegrationTestBase() {

    private val testDataRoot: Path = TestUtil.amperSourcesRoot.resolve("amper-backend-test/testData/projects")

    private suspend fun TestCollector.setupTestDataProject(
        testProjectName: String,
        programArgs: List<String> = emptyList(),
        copyToTemp: Boolean = false,
    ): CliContext = setupTestProject(
        testDataRoot.resolve(testProjectName),
        copyToTemp = copyToTemp,
        programArgs = programArgs,
    )

    @Test
    fun `ksp jvm autoservice`() = runTestWithCollector {
        val projectContext = setupTestDataProject("ksp-jvm-autoservice")
        val backend = AmperBackend(projectContext)
        backend.build()

        val generatedFilesDir = projectContext.generatedFilesDir(module = "service-impl", fragment = "jvm")
        generatedFilesDir.assertContainsRelativeFiles(
            "resources/ksp/META-INF/services/com.sample.service.MyService",
        )

        backend.runApplication()
        assertStdoutContains("Hello, service!")
    }

    @Test
    fun `ksp jvm local processor`() = runTestWithCollector {
        val projectContext = setupTestDataProject("ksp-jvm-local-processor")
        val backend = AmperBackend(projectContext)
        backend.build()

        val generatedFilesDir = projectContext.generatedFilesDir(module = "consumer", fragment = "jvm")
        generatedFilesDir.assertContainsRelativeFiles(
            "resources/ksp/com/sample/generated/annotated-classes.txt",
        )

        backend.runApplication()
        assertStdoutTextContains("""
            My annotated classes are:
            org.sample.ksp.localprocessor.consumer.B
            org.sample.ksp.localprocessor.consumer.A
        """.trimIndent())
    }

    @Test
    fun `ksp kmp local processor`() = runTestWithCollector {
        val projectContext = setupTestDataProject("ksp-kmp-local-processor")
        val backend = AmperBackend(projectContext)
        backend.build()

        projectContext.generatedFilesDir(module = "consumer", fragment = "jvm").assertContainsRelativeFiles(
            "classes/ksp/com/sample/myprocessor/gen/MyGeneratedClass.class",
            "resources/ksp/com/sample/myprocessor/gen/annotated-classes.txt",
            "src/ksp/java/com/sample/myprocessor/gen/MyCommonClassGeneratedJava.java",
            "src/ksp/java/com/sample/myprocessor/gen/MyJvmClassGeneratedJava.java",
            "src/ksp/kotlin/com/sample/myprocessor/gen/MyCommonClassGenerated.kt",
            "src/ksp/kotlin/com/sample/myprocessor/gen/MyJvmClassGenerated.kt",
        )

        projectContext.generatedFilesDir(module = "consumer", fragment = "android").assertContainsRelativeFiles(
            "classes/ksp/com/sample/myprocessor/gen/MyGeneratedClass.class",
            "resources/ksp/com/sample/myprocessor/gen/annotated-classes.txt",
            "src/ksp/java/com/sample/myprocessor/gen/MyAndroidClassGeneratedJava.java",
            "src/ksp/java/com/sample/myprocessor/gen/MyCommonClassGeneratedJava.java",
            "src/ksp/kotlin/com/sample/myprocessor/gen/MyAndroidClassGenerated.kt",
            "src/ksp/kotlin/com/sample/myprocessor/gen/MyCommonClassGenerated.kt",
        )

        projectContext.generatedFilesDir(module = "consumer", fragment = "mingw").assertContainsRelativeFiles(
            "resources/ksp/com/sample/myprocessor/gen/annotated-classes.txt",
            "src/ksp/kotlin/com/sample/myprocessor/gen/MyCommonClassGenerated.kt",
            "src/ksp/kotlin/com/sample/myprocessor/gen/MyMingwClassGenerated.kt",
            "src/ksp/kotlin/com/sample/myprocessor/gen/MyMingwX64ClassGenerated.kt",
            "src/ksp/kotlin/com/sample/myprocessor/gen/MyNativeClassGenerated.kt",
        )

        projectContext.generatedFilesDir(module = "consumer", fragment = "linux").assertContainsRelativeFiles(
            "resources/ksp/com/sample/myprocessor/gen/annotated-classes.txt",
            "src/ksp/kotlin/com/sample/myprocessor/gen/MyCommonClassGenerated.kt",
            "src/ksp/kotlin/com/sample/myprocessor/gen/MyLinuxX64ClassGenerated.kt",
            "src/ksp/kotlin/com/sample/myprocessor/gen/MyNativeClassGenerated.kt",
        )

        if (DefaultSystemInfo.detect().family.isMac) {
            projectContext.generatedFilesDir(module = "consumer", fragment = "iosArm64").assertContainsRelativeFiles(
                "resources/ksp/com/sample/myprocessor/gen/annotated-classes.txt",
                "src/ksp/kotlin/com/sample/myprocessor/gen/MyAppleClassGenerated.kt",
                "src/ksp/kotlin/com/sample/myprocessor/gen/MyCommonClassGenerated.kt",
                "src/ksp/kotlin/com/sample/myprocessor/gen/MyIosArm64ClassGenerated.kt",
                "src/ksp/kotlin/com/sample/myprocessor/gen/MyIosClassGenerated.kt",
                "src/ksp/kotlin/com/sample/myprocessor/gen/MyNativeClassGenerated.kt",
            )
            projectContext.generatedFilesDir(module = "consumer", fragment = "iosSimulatorArm64").assertContainsRelativeFiles(
                "resources/ksp/com/sample/myprocessor/gen/annotated-classes.txt",
                "src/ksp/kotlin/com/sample/myprocessor/gen/MyAppleClassGenerated.kt",
                "src/ksp/kotlin/com/sample/myprocessor/gen/MyCommonClassGenerated.kt",
                "src/ksp/kotlin/com/sample/myprocessor/gen/MyIosClassGenerated.kt",
                "src/ksp/kotlin/com/sample/myprocessor/gen/MyIosSimulatorArm64ClassGenerated.kt",
                "src/ksp/kotlin/com/sample/myprocessor/gen/MyNativeClassGenerated.kt",
            )
        }

        fun generatedResourceFor(fragment: String) =
            projectContext.generatedFilesDir(module = "consumer", fragment = fragment)
                .resolve("resources/ksp/com/sample/myprocessor/gen/annotated-classes.txt")

        generatedResourceFor(fragment = "jvm").assertContentEquals("""
            com.sample.ksp.localprocessor.consumer.MyJvmClass
            com.sample.ksp.localprocessor.consumer.MyCommonClass
        """.trimIndent())
        generatedResourceFor(fragment = "android").assertContentEquals("""
            com.sample.ksp.localprocessor.consumer.MyCommonClass
            com.sample.ksp.localprocessor.consumer.MyAndroidClass
        """.trimIndent())
        // mingw (and not mingwX64) because of how we collapse fragments right now
        generatedResourceFor(fragment = "mingw").assertContentEquals("""
            com.sample.ksp.localprocessor.consumer.MyNativeClass
            com.sample.ksp.localprocessor.consumer.MyMingwX64Class
            com.sample.ksp.localprocessor.consumer.MyMingwClass
            com.sample.ksp.localprocessor.consumer.MyCommonClass
        """.trimIndent())
        generatedResourceFor(fragment = "linux").assertContentEquals("""
            com.sample.ksp.localprocessor.consumer.MyNativeClass
            com.sample.ksp.localprocessor.consumer.MyLinuxX64Class
            com.sample.ksp.localprocessor.consumer.MyCommonClass
        """.trimIndent())

        if (DefaultSystemInfo.detect().family.isMac) {
            generatedResourceFor(fragment = "iosArm64").assertContentEquals("""
                com.sample.ksp.localprocessor.consumer.MyNativeClass
                com.sample.ksp.localprocessor.consumer.MyIosClass
                com.sample.ksp.localprocessor.consumer.MyIosArm64Class
                com.sample.ksp.localprocessor.consumer.MyCommonClass
                com.sample.ksp.localprocessor.consumer.MyAppleClass
            """.trimIndent())
            generatedResourceFor(fragment = "iosSimulatorArm64").assertContentEquals("""
                com.sample.ksp.localprocessor.consumer.MyNativeClass
                com.sample.ksp.localprocessor.consumer.MyIosSimulatorArm64Class
                com.sample.ksp.localprocessor.consumer.MyIosClass
                com.sample.ksp.localprocessor.consumer.MyCommonClass
                com.sample.ksp.localprocessor.consumer.MyAppleClass
            """.trimIndent())
        }
    }

    @Test
    fun `ksp jvm dagger`() = runTestWithCollector {
        val projectContext = setupTestDataProject("ksp-jvm-dagger")
        val backend = AmperBackend(projectContext)
        backend.build()

        val generatedFilesDir = projectContext.generatedFilesDir(module = "ksp-jvm-dagger", fragment = "jvm")
        generatedFilesDir.assertContainsRelativeFiles(
            "src/ksp/java/com/sample/dagger/CoffeeMaker_Factory.java",
            "src/ksp/java/com/sample/dagger/DaggerCoffeeShop.java",
            "src/ksp/java/com/sample/dagger/HeaterModule_Companion_ProvideHeaterFactory.java",
            "src/ksp/java/com/sample/dagger/Heater_Factory.java",
        )

        backend.runApplication()
        assertStdoutContains("Heater: heating...")
        assertStdoutContains("CoffeeMaker: brewing...")
    }

    @Test
    fun `ksp jvm dagger with catalog refs`() = runTestWithCollector {
        val projectContext = setupTestDataProject("ksp-jvm-dagger-catalog")
        val backend = AmperBackend(projectContext)
        backend.build()

        val generatedFilesDir = projectContext.generatedFilesDir(module = "ksp-jvm-dagger-catalog", fragment = "jvm")
        generatedFilesDir.assertContainsRelativeFiles(
            "src/ksp/java/com/sample/dagger/CoffeeMaker_Factory.java",
            "src/ksp/java/com/sample/dagger/DaggerCoffeeShop.java",
            "src/ksp/java/com/sample/dagger/HeaterModule_Companion_ProvideHeaterFactory.java",
            "src/ksp/java/com/sample/dagger/Heater_Factory.java",
        )

        backend.runApplication()
        assertStdoutContains("Heater: heating...")
        assertStdoutContains("CoffeeMaker: brewing...")
    }

    @Test
    fun `ksp android room`() = runTestWithCollector {
        val projectContext = setupTestDataProject("ksp-android-room")
        val generatedSchemaPath = projectContext.projectRoot.path / "generated-db-schema"
        generatedSchemaPath.deleteRecursively()

        val backend = AmperBackend(projectContext)
        backend.build()

        val generatedFilesDir = projectContext.generatedFilesDir(module = "ksp-android-room", fragment = "android")

        generatedFilesDir.assertContainsRelativeFiles(
            "src/ksp/kotlin/com/jetbrains/sample/app/AppDatabase_Impl.kt",
            "src/ksp/kotlin/com/jetbrains/sample/app/UserDao_Impl.kt",
        )
        generatedSchemaPath.assertContainsRelativeFiles(
            "com.jetbrains.sample.app.AppDatabase/1.json",
        )
    }

    // TODO Enable when Koin supports KSP2
    @Disabled("Koin doesn't support KSP2 yet: https://github.com/InsertKoinIO/koin-annotations/issues/132")
    @Test
    fun `ksp jvm koin`() = runTestWithCollector {
        val projectContext = setupTestDataProject("ksp-jvm-koin")
        val backend = AmperBackend(projectContext)
        backend.build()

        val generatedFilesDir = projectContext.generatedFilesDir(module = "ksp-jvm-koin", fragment = "jvm")
        generatedFilesDir.assertContainsRelativeFiles(
            "src/ksp/kotlin/org/koin/ksp/generated/CoffeeShopModuleGencom\$sample\$koin.kt",
            "src/ksp/kotlin/org/koin/ksp/generated/KoinMeta-1876525009.kt",
        )

        backend.runApplication()
        assertStdoutContains("Starting Koin...")
        assertStdoutContains("Hello, Koin!")
        assertStdoutContains("Heater: heating...")
        assertStdoutContains("CoffeeMaker: brewing...")
    }

    // TODO Enable when Koin supports KSP2
    @Disabled("Koin doesn't support KSP2 yet: https://github.com/InsertKoinIO/koin-annotations/issues/132")
    @Test
    fun `ksp multiplatform koin`() = runTestWithCollector {
        val projectContext = setupTestDataProject("ksp-kmp-koin")
        val backend = AmperBackend(projectContext)
        backend.runTask(TaskName(":ksp-kmp-koin:kspJvm"))

        projectContext.generatedFilesDir(module = "ksp-kmp-koin", fragment = "jvm").assertContainsRelativeFiles(
            "src/ksp/kotlin/org/koin/ksp/generated/CoffeeShopModuleGencom\$sample\$koin.kt",
            "src/ksp/kotlin/org/koin/ksp/generated/KoinMeta-1876525009.kt",
        )

        val os = DefaultSystemInfo.detect()
        if (os.family.isWindows) {
            projectContext.generatedFilesDir(module = "shared", fragment = "mingwX64").assertContainsRelativeFiles(
                "src/ksp/kotlin/org/koin/ksp/generated/CoffeeShopModuleGencom\$sample\$koin.kt",
                "src/ksp/kotlin/org/koin/ksp/generated/KoinMeta-1876525009.kt",
            )
        }

        if (os.family.isMac) {
            if (os.arch == Arch.Arm64) {
                projectContext.generatedFilesDir(module = "shared", fragment = "macosArm64")
                    .assertContainsRelativeFiles(
                        "src/ksp/kotlin/org/koin/ksp/generated/CoffeeShopModuleGencom\$sample\$koin.kt",
                        "src/ksp/kotlin/org/koin/ksp/generated/KoinMeta-1876525009.kt",
                    )
            }
            if (os.arch == Arch.X64) {
                projectContext.generatedFilesDir(module = "shared", fragment = "macosX64")
                    .assertContainsRelativeFiles(
                        "src/ksp/kotlin/org/koin/ksp/generated/CoffeeShopModuleGencom\$sample\$koin.kt",
                        "src/ksp/kotlin/org/koin/ksp/generated/KoinMeta-1876525009.kt",
                    )
            }
        }
    }

    @Test
    fun `compose multiplatform room`() = runTestWithCollector {
        val projectContext = setupTestDataProject("compose-multiplatform-room")
        val generatedSchemaPath = projectContext.projectRoot.path / "shared/generated-db-schema"
        generatedSchemaPath.deleteRecursively()

        val backend = AmperBackend(projectContext)
        backend.build()

        projectContext.generatedFilesDir(module = "shared", fragment = "android").assertContainsRelativeFiles(
            "src/ksp/kotlin/AppDatabase_Impl.kt",
            "src/ksp/kotlin/TodoDao_Impl.kt",
        )
        projectContext.generatedFilesDir(module = "shared", fragment = "jvm").assertContainsRelativeFiles(
            "src/ksp/kotlin/AppDatabase_Impl.kt",
            "src/ksp/kotlin/TodoDao_Impl.kt",
        )

        // TODO [KSP2_ISSUE] enable when KSP native bugs are fixed
        // mingwX64 is not supported yet by room (despite the DR succeeding)
        // apple targets or linuxX64 cause the following error:
        // NullPointerException: null cannot be cast to non-null type org.jetbrains.kotlin.load.java.structure.impl.JavaClassImpl
        // Related:
        // https://github.com/google/ksp/issues/1823 (fix will be released with Kotlin 2.1 - KSP 2.1.0-1.0.x)
        // https://github.com/google/ksp/issues/2112 (should be fixed in 1.0.26)
        // https://github.com/google/ksp/issues/885#issuecomment-1933378627
        // https://issuetracker.google.com/issues/359279551

//        if (DefaultSystemInfo.detect().family.isMac) {
//            projectContext.generatedFilesDir(module = "shared", fragment = "iosSimulatorArm64").assertContainsRelativeFiles(
//                "src/ksp/kotlin/AppDatabase_Impl.kt",
//                "src/ksp/kotlin/TodoDao_Impl.kt",
//            )
//        }

        generatedSchemaPath.assertContainsRelativeFiles(
            "android/AppDatabase/1.json",
            "jvm/AppDatabase/1.json",
        )
    }

    private fun CliContext.generatedFilesDir(module: String, fragment: String): Path =
        buildOutputRoot.path / "generated" / module / fragment
}

/**
 * Asserts that the directory at this [Path] contains all the files at the given [expectedRelativePaths].
 */
private fun Path.assertContainsRelativeFiles(vararg expectedRelativePaths: String) {
    val actualFiles = walk().map { it.relativeTo(this) }.sorted().toList()
    val expectedFiles = expectedRelativePaths.map { Path(it) }
    assertEquals(expectedFiles, actualFiles)
}

private fun Path.assertContentEquals(expectedContents: String) {
    assertTrue(exists(), "Expected file $pathString to exist")
    assertTrue(isRegularFile(), "Expected a file but got a directory: $pathString")
    assertEquals(expectedContents, readText())
}
