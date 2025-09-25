/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertContainsRelativeFiles
import org.jetbrains.amper.cli.test.utils.assertSomeStderrLineContains
import org.jetbrains.amper.cli.test.utils.assertSomeStdoutLineContains
import org.jetbrains.amper.cli.test.utils.assertStderrDoesNotContain
import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.core.system.Arch
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.test.AmperCliResult
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class KspTest: AmperCliTestBase() {

    @Test
    fun `ksp jvm invalid kotlin version`() = runSlowTest {
        val projectRoot = testProject("ksp-jvm-invalid-kotlin-version")
        val buildResult = runCli(projectRoot, "build", expectedExitCode = 1, assertEmptyStdErr = false)

        // KSP shouldn't crash
        buildResult.assertStderrDoesNotContain("Internal error")
        buildResult.assertSomeStderrLineContains("Invalid Kotlin compiler version 2. Should be in the format 'x.y.*'.")
    }

    @Test
    fun `ksp jvm autoservice`() = runSlowTest {
        val projectRoot = testProject("ksp-jvm-autoservice")
        val buildResult = runCli(projectRoot, "build")

        val generatedFilesDir = buildResult.generatedFilesDir(module = "service-impl", fragment = "jvm")
        generatedFilesDir.assertContainsRelativeFiles(
            "resources/ksp/META-INF/services/com.sample.service.MyService",
        )

        val runResult = runCli(projectRoot, "run")
        runResult.assertSomeStdoutLineContains("Hello, service!")
    }

    @Test
    fun `ksp jvm local processor`() = runSlowTest {
        val projectRoot = testProject("ksp-jvm-local-processor")
        val buildResult = runCli(projectRoot, "build")

        val generatedFilesDir = buildResult.generatedFilesDir(module = "consumer", fragment = "main")
        generatedFilesDir.assertContainsRelativeFiles(
            "resources/ksp/com/sample/generated/annotated-classes.txt",
        )

        val runResult = runCli(projectRoot, "run")
        runResult.assertStdoutContains("""
            My annotated classes are:
            org.sample.ksp.localprocessor.consumer.B
            org.sample.ksp.localprocessor.consumer.A
        """.trimIndent())
    }

    @Test
    fun `ksp kmp local processor`() = runSlowTest {
        val projectRoot = testProject("ksp-kmp-local-processor")
        val buildResult = runCli(projectRoot, "build", configureAndroidHome = true)

        buildResult.generatedFilesDir(module = "consumer", fragment = "jvm").assertContainsRelativeFiles(
            "classes/ksp/com/sample/myprocessor/gen/MyGeneratedClass.class",
            "resources/ksp/com/sample/myprocessor/gen/annotated-classes.txt",
            "src/ksp/java/com/sample/myprocessor/gen/MyCommonClassGeneratedJava.java",
            "src/ksp/java/com/sample/myprocessor/gen/MyJvmClassGeneratedJava.java",
            "src/ksp/kotlin/com/sample/myprocessor/gen/MyCommonClassGenerated.kt",
            "src/ksp/kotlin/com/sample/myprocessor/gen/MyJvmClassGenerated.kt",
        )

        buildResult.generatedFilesDir(module = "consumer", fragment = "android").assertContainsRelativeFiles(
            "classes/ksp/com/sample/myprocessor/gen/MyGeneratedClass.class",
            "resources/ksp/com/sample/myprocessor/gen/annotated-classes.txt",
            "src/ksp/java/com/sample/myprocessor/gen/MyAndroidClassGeneratedJava.java",
            "src/ksp/java/com/sample/myprocessor/gen/MyCommonClassGeneratedJava.java",
            "src/ksp/kotlin/com/sample/myprocessor/gen/MyAndroidClassGenerated.kt",
            "src/ksp/kotlin/com/sample/myprocessor/gen/MyCommonClassGenerated.kt",
        )

        buildResult.generatedFilesDir(module = "consumer", fragment = "mingwX64").assertContainsRelativeFiles(
            "resources/ksp/com/sample/myprocessor/gen/annotated-classes.txt",
            "src/ksp/kotlin/com/sample/myprocessor/gen/MyCommonClassGenerated.kt",
            "src/ksp/kotlin/com/sample/myprocessor/gen/MyMingwClassGenerated.kt",
            "src/ksp/kotlin/com/sample/myprocessor/gen/MyMingwX64ClassGenerated.kt",
            "src/ksp/kotlin/com/sample/myprocessor/gen/MyNativeClassGenerated.kt",
        )

        buildResult.generatedFilesDir(module = "consumer", fragment = "linuxX64").assertContainsRelativeFiles(
            "resources/ksp/com/sample/myprocessor/gen/annotated-classes.txt",
            "src/ksp/kotlin/com/sample/myprocessor/gen/MyCommonClassGenerated.kt",
            "src/ksp/kotlin/com/sample/myprocessor/gen/MyLinuxX64ClassGenerated.kt",
            "src/ksp/kotlin/com/sample/myprocessor/gen/MyNativeClassGenerated.kt",
        )

        if (DefaultSystemInfo.detect().family.isMac) {
            buildResult.generatedFilesDir(module = "consumer", fragment = "iosArm64").assertContainsRelativeFiles(
                "resources/ksp/com/sample/myprocessor/gen/annotated-classes.txt",
                "src/ksp/kotlin/com/sample/myprocessor/gen/MyAppleClassGenerated.kt",
                "src/ksp/kotlin/com/sample/myprocessor/gen/MyCommonClassGenerated.kt",
                "src/ksp/kotlin/com/sample/myprocessor/gen/MyIosArm64ClassGenerated.kt",
                "src/ksp/kotlin/com/sample/myprocessor/gen/MyIosClassGenerated.kt",
                "src/ksp/kotlin/com/sample/myprocessor/gen/MyNativeClassGenerated.kt",
            )
            buildResult.generatedFilesDir(module = "consumer", fragment = "iosSimulatorArm64").assertContainsRelativeFiles(
                "resources/ksp/com/sample/myprocessor/gen/annotated-classes.txt",
                "src/ksp/kotlin/com/sample/myprocessor/gen/MyAppleClassGenerated.kt",
                "src/ksp/kotlin/com/sample/myprocessor/gen/MyCommonClassGenerated.kt",
                "src/ksp/kotlin/com/sample/myprocessor/gen/MyIosClassGenerated.kt",
                "src/ksp/kotlin/com/sample/myprocessor/gen/MyIosSimulatorArm64ClassGenerated.kt",
                "src/ksp/kotlin/com/sample/myprocessor/gen/MyNativeClassGenerated.kt",
            )
        }

        fun generatedResourceFor(fragment: String) =
            buildResult.generatedFilesDir(module = "consumer", fragment = fragment)
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
        generatedResourceFor(fragment = "mingwX64").assertContentEquals("""
            com.sample.ksp.localprocessor.consumer.MyNativeClass
            com.sample.ksp.localprocessor.consumer.MyMingwX64Class
            com.sample.ksp.localprocessor.consumer.MyMingwClass
            com.sample.ksp.localprocessor.consumer.MyCommonClass
        """.trimIndent())
        generatedResourceFor(fragment = "linuxX64").assertContentEquals("""
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
    fun `ksp jvm dagger`() = runSlowTest {
        val projectRoot = testProject("ksp-jvm-dagger")
        val buildResult = runCli(projectRoot, "build")

        val generatedFilesDir = buildResult.generatedFilesDir(module = "ksp-jvm-dagger", fragment = "main")
        generatedFilesDir.assertContainsRelativeFiles(
            "src/ksp/java/com/sample/dagger/CoffeeMaker_Factory.java",
            "src/ksp/java/com/sample/dagger/DaggerCoffeeShop.java",
            "src/ksp/java/com/sample/dagger/HeaterModule_Companion_ProvideHeaterFactory.java",
            "src/ksp/java/com/sample/dagger/Heater_Factory.java",
        )

        val runResult = runCli(projectRoot, "run")
        runResult.assertSomeStdoutLineContains("Heater: heating...")
        runResult.assertSomeStdoutLineContains("CoffeeMaker: brewing...")
    }

    @Test
    fun `ksp jvm dagger with catalog refs`() = runSlowTest {
        val projectRoot = testProject("ksp-jvm-dagger-catalog")
        val buildResult = runCli(projectRoot, "build")

        val generatedFilesDir = buildResult.generatedFilesDir(module = "ksp-jvm-dagger-catalog", fragment = "main")
        generatedFilesDir.assertContainsRelativeFiles(
            "src/ksp/java/com/sample/dagger/CoffeeMaker_Factory.java",
            "src/ksp/java/com/sample/dagger/DaggerCoffeeShop.java",
            "src/ksp/java/com/sample/dagger/HeaterModule_Companion_ProvideHeaterFactory.java",
            "src/ksp/java/com/sample/dagger/Heater_Factory.java",
        )

        val runResult = runCli(projectRoot, "run")
        runResult.assertSomeStdoutLineContains("Heater: heating...")
        runResult.assertSomeStdoutLineContains("CoffeeMaker: brewing...")
    }

    @Test
    fun `ksp android room`() = runSlowTest {
        val projectRoot = testProject("ksp-android-room")
        val generatedSchemaPath = projectRoot / "generated-db-schema"
        generatedSchemaPath.deleteRecursively()

        val buildResult = runCli(projectRoot, "build", configureAndroidHome = true)

        val generatedFilesDir = buildResult.generatedFilesDir(module = "ksp-android-room", fragment = "main")

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
    fun `ksp jvm koin`() = runSlowTest {
        val projectRoot = testProject("ksp-jvm-koin")
        val buildResult = runCli(projectRoot, "build")

        val generatedFilesDir = buildResult.generatedFilesDir(module = "ksp-jvm-koin", fragment = "jvm")
        generatedFilesDir.assertContainsRelativeFiles(
            "src/ksp/kotlin/org/koin/ksp/generated/CoffeeShopModuleGencom\$sample\$koin.kt",
            "src/ksp/kotlin/org/koin/ksp/generated/KoinMeta-1876525009.kt",
        )

        val runResult = runCli(projectRoot, "run")
        runResult.assertSomeStdoutLineContains("Starting Koin...")
        runResult.assertSomeStdoutLineContains("Hello, Koin!")
        runResult.assertSomeStdoutLineContains("Heater: heating...")
        runResult.assertSomeStdoutLineContains("CoffeeMaker: brewing...")
    }

    // TODO Enable when Koin supports KSP2
    @Disabled("Koin doesn't support KSP2 yet: https://github.com/InsertKoinIO/koin-annotations/issues/132")
    @Test
    fun `ksp multiplatform koin`() = runSlowTest {
        val kspResult = runCli(testProject("ksp-kmp-koin"), "task", ":ksp-kmp-koin:kspJvm")

        kspResult.generatedFilesDir(module = "ksp-kmp-koin", fragment = "jvm").assertContainsRelativeFiles(
            "src/ksp/kotlin/org/koin/ksp/generated/CoffeeShopModuleGencom\$sample\$koin.kt",
            "src/ksp/kotlin/org/koin/ksp/generated/KoinMeta-1876525009.kt",
        )

        val os = DefaultSystemInfo.detect()
        if (os.family.isWindows) {
            kspResult.generatedFilesDir(module = "shared", fragment = "mingwX64").assertContainsRelativeFiles(
                "src/ksp/kotlin/org/koin/ksp/generated/CoffeeShopModuleGencom\$sample\$koin.kt",
                "src/ksp/kotlin/org/koin/ksp/generated/KoinMeta-1876525009.kt",
            )
        }

        if (os.family.isMac) {
            if (os.arch == Arch.Arm64) {
                kspResult.generatedFilesDir(module = "shared", fragment = "macosArm64")
                    .assertContainsRelativeFiles(
                        "src/ksp/kotlin/org/koin/ksp/generated/CoffeeShopModuleGencom\$sample\$koin.kt",
                        "src/ksp/kotlin/org/koin/ksp/generated/KoinMeta-1876525009.kt",
                    )
            }
            if (os.arch == Arch.X64) {
                kspResult.generatedFilesDir(module = "shared", fragment = "macosX64")
                    .assertContainsRelativeFiles(
                        "src/ksp/kotlin/org/koin/ksp/generated/CoffeeShopModuleGencom\$sample\$koin.kt",
                        "src/ksp/kotlin/org/koin/ksp/generated/KoinMeta-1876525009.kt",
                    )
            }
        }
    }

    @Test
    fun `compose multiplatform room`() = runSlowTest {
        val projectRoot = testProject("compose-multiplatform-room")
        val generatedSchemaPath = projectRoot / "shared/generated-db-schema"
        generatedSchemaPath.deleteRecursively()

        // [AMPER-3957]: Should be changed back to build for all platforms
        // AMPER-395 is fixed, but some other stuff is still broken that prevent it from being uncommented
        val buildResult = runCli(
            projectRoot,
            "build", "--platform=jvm", /* "--platform=android", */
            configureAndroidHome = true,
        )

        // [AMPER-3957]:
//        buildResult.generatedFilesDir(module = "shared", fragment = "android").assertContainsRelativeFiles(
//            "src/ksp/kotlin/AppDatabase_Impl.kt",
//            "src/ksp/kotlin/TodoDao_Impl.kt",
//        )
        buildResult.generatedFilesDir(module = "shared", fragment = "jvm").assertContainsRelativeFiles(
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
//            buildResult.generatedFilesDir(module = "shared", fragment = "iosSimulatorArm64").assertContainsRelativeFiles(
//                "src/ksp/kotlin/AppDatabase_Impl.kt",
//                "src/ksp/kotlin/TodoDao_Impl.kt",
//            )
//        }

        generatedSchemaPath.assertContainsRelativeFiles(
            // [AMPER-3957]:
//            "android/AppDatabase/1.json",
            "jvm/AppDatabase/1.json",
        )
    }

    private fun AmperCliResult.generatedFilesDir(module: String, fragment: String): Path =
        buildOutputRoot / "generated" / module / fragment
}

private fun Path.assertContentEquals(expectedContents: String) {
    assertTrue(exists(), "Expected file $pathString to exist")
    assertTrue(isRegularFile(), "Expected a file but got a directory: $pathString")
    assertEquals(expectedContents, readText())
}
