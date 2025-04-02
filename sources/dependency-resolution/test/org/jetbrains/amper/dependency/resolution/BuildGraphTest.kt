/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.TestInfo
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BuildGraphTest: BaseDRTest() {

    /**
     * This test checks that wrong artifact checksum declared in the Gradle module metadata won't cause DR error if
     * valid checksum is published as a separate file in an external repository.
     *
     * Library 'com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.18.2' declares jvm variant that
     * provides the following invalid sha1 checksum for the artifact 'jackson-datatype-jdk8-2.18.2.jar':
     * 'd85b6fb492cde96f937b62270113ed40698560de'.
     * The valid one is published in the maven central repository in the file 'jackson-datatype-jdk8-2.18.2.jar.sha1':
     * '9ed6d538ebcc66864e114a7040953dce6ab6ea53'
      */
    @Test
    fun `com_fasterxml_jackson_datatype jackson-datatype-jdk8 2_18_2`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            expected = """root
               |\--- com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.18.2
               |     +--- com.fasterxml.jackson.core:jackson-core:2.18.2
               |     |    \--- com.fasterxml.jackson:jackson-bom:2.18.2
               |     +--- com.fasterxml.jackson.core:jackson-databind:2.18.2
               |     |    +--- com.fasterxml.jackson.core:jackson-annotations:2.18.2
               |     |    |    \--- com.fasterxml.jackson:jackson-bom:2.18.2
               |     |    +--- com.fasterxml.jackson.core:jackson-core:2.18.2 (*)
               |     |    \--- com.fasterxml.jackson:jackson-bom:2.18.2
               |     \--- com.fasterxml.jackson:jackson-bom:2.18.2
            """.trimMargin()
        )
        runBlocking {
            downloadAndAssertFiles(
                """jackson-annotations-2.18.2-sources.jar
               |jackson-annotations-2.18.2.jar
               |jackson-core-2.18.2-sources.jar
               |jackson-core-2.18.2.jar
               |jackson-databind-2.18.2-sources.jar
               |jackson-databind-2.18.2.jar
               |jackson-datatype-jdk8-2.18.2-sources.jar
               |jackson-datatype-jdk8-2.18.2.jar""".trimMargin(),
                root, true, verifyMessages = true
            )
        }
    }

    /**
     * This test checks that a POM file of dependency could use properties defined in the parent POM
     * (or in any of its parents).
     *
     * In this example,
     * library 'org.springframework.data:spring-data-jpa:3.4.2' has a parent POM:
     * `org.springframework.data:spring-data-jpa-parent:3.4.2`
     * which in turn defines the 'dependencyManagement' section
     * where 'org.testcontainers:testcontainers-bom' is imported.
     * The version of 'testcontainers-bom' is defined as '${testcontainers}'.
     * And the value of the property 'testcontainers' is defined in the parent POM in turn:
     * in `org.springframework.data.build:spring-data-parent:3.4.2`.
     *
     * The test ensures that the value of the property 'testcontainers' is correctly resolved from the parent POM
     * when it comes to resolving the 'dependencyManagement' section in the child project.
     *
     * Note: 'dependencyManagement' sections declared in the project parents POMs should be resolved,
     * to further use it for resolving the dependencies' versions of the
     * project module being processed.
     */
    @Test
    fun `org_springframework_data spring-data-jpa 3_4_2`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            expected = """root
               |\--- org.springframework.data:spring-data-jpa:3.4.2
               |     +--- org.springframework.data:spring-data-commons:3.4.2
               |     |    +--- org.springframework:spring-core:6.2.2
               |     |    |    \--- org.springframework:spring-jcl:6.2.2
               |     |    +--- org.springframework:spring-beans:6.2.2
               |     |    |    \--- org.springframework:spring-core:6.2.2 (*)
               |     |    \--- org.slf4j:slf4j-api:2.0.2
               |     +--- org.springframework:spring-orm:6.2.2
               |     |    +--- org.springframework:spring-beans:6.2.2 (*)
               |     |    +--- org.springframework:spring-core:6.2.2 (*)
               |     |    +--- org.springframework:spring-jdbc:6.2.2
               |     |    |    +--- org.springframework:spring-beans:6.2.2 (*)
               |     |    |    +--- org.springframework:spring-core:6.2.2 (*)
               |     |    |    \--- org.springframework:spring-tx:6.2.2
               |     |    |         +--- org.springframework:spring-beans:6.2.2 (*)
               |     |    |         \--- org.springframework:spring-core:6.2.2 (*)
               |     |    \--- org.springframework:spring-tx:6.2.2 (*)
               |     +--- org.springframework:spring-context:6.2.2
               |     |    +--- org.springframework:spring-aop:6.2.2
               |     |    |    +--- org.springframework:spring-beans:6.2.2 (*)
               |     |    |    \--- org.springframework:spring-core:6.2.2 (*)
               |     |    +--- org.springframework:spring-beans:6.2.2 (*)
               |     |    +--- org.springframework:spring-core:6.2.2 (*)
               |     |    +--- org.springframework:spring-expression:6.2.2
               |     |    |    \--- org.springframework:spring-core:6.2.2 (*)
               |     |    \--- io.micrometer:micrometer-observation:1.14.3
               |     |         \--- io.micrometer:micrometer-commons:1.14.3
               |     +--- org.springframework:spring-aop:6.2.2 (*)
               |     +--- org.springframework:spring-tx:6.2.2 (*)
               |     +--- org.springframework:spring-beans:6.2.2 (*)
               |     +--- org.springframework:spring-core:6.2.2 (*)
               |     +--- org.antlr:antlr4-runtime:4.13.0
               |     +--- jakarta.annotation:jakarta.annotation-api:2.0.0
               |     \--- org.slf4j:slf4j-api:2.0.2
            """.trimMargin()
        )
        runBlocking {
            downloadAndAssertFiles(
                """antlr4-runtime-4.13.0-sources.jar
               |antlr4-runtime-4.13.0.jar
               |jakarta.annotation-api-2.0.0-sources.jar
               |jakarta.annotation-api-2.0.0.jar
               |micrometer-commons-1.14.3-sources.jar
               |micrometer-commons-1.14.3.jar
               |micrometer-observation-1.14.3-sources.jar
               |micrometer-observation-1.14.3.jar
               |slf4j-api-2.0.2-sources.jar
               |slf4j-api-2.0.2.jar
               |spring-aop-6.2.2-sources.jar
               |spring-aop-6.2.2.jar
               |spring-beans-6.2.2-sources.jar
               |spring-beans-6.2.2.jar
               |spring-context-6.2.2-sources.jar
               |spring-context-6.2.2.jar
               |spring-core-6.2.2-sources.jar
               |spring-core-6.2.2.jar
               |spring-data-commons-3.4.2-sources.jar
               |spring-data-commons-3.4.2.jar
               |spring-data-jpa-3.4.2-sources.jar
               |spring-data-jpa-3.4.2.jar
               |spring-expression-6.2.2-sources.jar
               |spring-expression-6.2.2.jar
               |spring-jcl-6.2.2-sources.jar
               |spring-jcl-6.2.2.jar
               |spring-jdbc-6.2.2-sources.jar
               |spring-jdbc-6.2.2.jar
               |spring-orm-6.2.2-sources.jar
               |spring-orm-6.2.2.jar
               |spring-tx-6.2.2-sources.jar
               |spring-tx-6.2.2.jar""".trimMargin(),
                root, true, verifyMessages = true
            )
        }
    }

    @Test
    fun `org_jetbrains_kotlin kotlin-test 1_9_10`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            expected = """root
                |\--- org.jetbrains.kotlin:kotlin-test:1.9.10
                |     \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.10
                |          +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.10
                |          \--- org.jetbrains:annotations:13.0
            """.trimMargin()
        )
        assertFiles(
            """annotations-13.0-sources.jar
                |annotations-13.0.jar
                |kotlin-stdlib-1.9.10-sources.jar
                |kotlin-stdlib-1.9.10.jar
                |kotlin-stdlib-common-1.9.10-sources.jar
                |kotlin-stdlib-common-1.9.10.jar
                |kotlin-test-1.9.10-sources.jar
                |kotlin-test-1.9.10.jar""".trimMargin(),
            root, true
        )
    }
    // todo (AB) : Test case II: artifact HASH file is corrupted (wrong checksum inside), check that
    // todo (AB) : - HASH file is re-downloaded
    // todo (AB) : - artifact is correct (re-downloaded if it was corrupted)
    // todo (AB) : - no errors is associated with corresponding dependency

    /**
     * This test checks that packaging type 'eclipse-plugin' of dependency is successfully resolved to the downloaded JAR file
     */
    @Test
    fun `org_eclipse_sisu org_eclipse_sisu_inject 0_3_5`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            expected = """root
                |\--- org.eclipse.sisu:org.eclipse.sisu.inject:0.3.5
            """.trimMargin()
        )
        runBlocking {
            downloadAndAssertFiles(
                """org.eclipse.sisu.inject-0.3.5-sources.jar
                |org.eclipse.sisu.inject-0.3.5.jar""".trimMargin(),
                root, true
            )
        }
    }

    /**
     * This test checks that if one of the repositories fails to resolve dependency
     * and return some incorrect/unexpected response,
     * dependency should still be resolved successfully from the valid repository
     */
    @Test
    fun `com_squareup_retrofit2 retrofit 2_11_0`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            scope = ResolutionScope.COMPILE,
            platform = setOf(ResolutionPlatform.JVM),
            repositories = listOf("https://jetbrains.team/p/amper/reviews/", REDIRECTOR_MAVEN_CENTRAL,),

            expected = """root
                |\--- com.squareup.retrofit2:retrofit:2.11.0
                |     \--- com.squareup.okhttp3:okhttp:3.14.9
                |          \--- com.squareup.okio:okio:1.17.2
            """.trimMargin()
        )
        runBlocking {
            downloadAndAssertFiles(
                """okhttp-3.14.9-sources.jar
                |okhttp-3.14.9.jar
                |okio-1.17.2-sources.jar
                |okio-1.17.2.jar
                |retrofit-2.11.0-sources.jar
                |retrofit-2.11.0.jar""".trimMargin(),
                root, true
            )
        }
    }

    /**
     * This test (and the next one) checks that Gradle metadata variant attribute 'org.gradle.jvm.environment'
     * is taken into account while resolving jvm/android-specific dependencies.
     *
     * Gradle metadata of Library 'androidx.sqlite:sqlite:2.5.0-alpha11' defines variants for both jvm and android
     * platforms, but all of them target JVM platform (i.e., attribute 'org.jetbrains.kotlin.platform.type' is set to 'jvm'),
     * the difference between android and jvm variants is in attribute 'org.gradle.jvm.environment' only.
     * For Android-specific variant the attribute is set to 'android' and for jvm-specific - to 'standard-jvm'.
     *
     * Before the corresponding fix, both variants were picked up by DR, which in turn caused runtime exception due to class duplication
     * because both variants contain the same classes.
     * See https://youtrack.jetbrains.com/issue/AMPER-3957 for more details.
     */
    @Test
    fun `androidx_sqlite sqlite 2_5_0-alpha11 android`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            dependency = "androidx.sqlite:sqlite:2.5.0-alpha11",
            scope = ResolutionScope.RUNTIME,
            platform = setOf(ResolutionPlatform.ANDROID),
            repositories = listOf( REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_MAVEN_GOOGLE ),

            expected = """root
                |\--- androidx.sqlite:sqlite:2.5.0-alpha11
                |     \--- androidx.sqlite:sqlite-android:2.5.0-alpha11
                |          +--- androidx.annotation:annotation:1.8.1
                |          |    \--- androidx.annotation:annotation-jvm:1.8.1
                |          |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> 1.8.22
                |          |              +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22
                |          |              \--- org.jetbrains:annotations:13.0
                |          \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 (*)
            """.trimMargin()
        )
        runBlocking {
            downloadAndAssertFiles(
                """annotation-jvm-1.8.1.jar
                |annotations-13.0.jar
                |kotlin-stdlib-1.8.22.jar
                |kotlin-stdlib-common-1.8.22.jar
                |sqlite-android-2.5.0-alpha11.aar""".trimMargin(),
                root
            )
        }
    }

    @Test
    fun `androidx_sqlite sqlite 2_5_0-alpha11 jvm`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            dependency = "androidx.sqlite:sqlite:2.5.0-alpha11",
            scope = ResolutionScope.RUNTIME,
            platform = setOf(ResolutionPlatform.JVM),
            repositories = listOf( REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_MAVEN_GOOGLE ),

            expected = """root
                |\--- androidx.sqlite:sqlite:2.5.0-alpha11
                |     \--- androidx.sqlite:sqlite-jvm:2.5.0-alpha11
                |          +--- androidx.annotation:annotation:1.8.1
                |          |    \--- androidx.annotation:annotation-jvm:1.8.1
                |          |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> 1.8.22
                |          |              +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22
                |          |              \--- org.jetbrains:annotations:13.0
                |          \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 (*)
            """.trimMargin()
        )
        runBlocking {
            downloadAndAssertFiles(
                """annotation-jvm-1.8.1.jar
                |annotations-13.0.jar
                |kotlin-stdlib-1.8.22.jar
                |kotlin-stdlib-common-1.8.22.jar
                |sqlite-jvm-2.5.0-alpha11.jar""".trimMargin(),
                root
            )
        }
    }

    @Test
    fun `com_google_guava listenablefuture 9999_0-empty-to-avoid-conflict-with-guava`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_MAVEN_GOOGLE, REDIRECTOR_COMPOSE_DEV),
            platform = setOf(ResolutionPlatform.JVM),
            expected = """root
                |\--- com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava
            """.trimMargin()
        )
        runBlocking{
            downloadAndAssertFiles(
                """listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar""".trimMargin()
                , root
                , withSources = true
                , checkAutoAddedDocumentation = false
            )
        }
    }

    @Test
    fun `org_jetbrains_kotlin kotlin-test-annotations-common 2_0_0`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            platform = setOf(
                ResolutionPlatform.IOS_X64,
                ResolutionPlatform.IOS_ARM64,
                ResolutionPlatform.IOS_SIMULATOR_ARM64
            ),
            expected = """root
                |\--- org.jetbrains.kotlin:kotlin-test-annotations-common:2.0.0
                |     \--- org.jetbrains.kotlin:kotlin-test:2.0.0
                |          \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.0
            """.trimMargin()
        )
        runBlocking {
            downloadAndAssertFiles(
                """kotlin-stdlib-commonMain-2.0.0-sources.jar
                |kotlin-stdlib-commonMain-2.0.0.klib
                |kotlin-test-annotationsCommonMain-2.0.0-sources.jar
                |kotlin-test-annotationsCommonMain-2.0.0.klib
                |kotlin-test-assertionsCommonMain-2.0.0-sources.jar
                |kotlin-test-assertionsCommonMain-2.0.0.klib""".trimMargin(),
                withSources = true,
                root = root
            )
        }
    }

    /**
     * It checks that a single version interval is supported.
     * `com.google.android.gms:play-services-measurement-api:22.1.0` depends on version `[22.1.0]` of
     * `com.google.android.gms:play-services-measurement-base`
     */
    @Test
    fun `com_google_android_gms play-services-measurement-api 22_1_0`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            platform = setOf(
                ResolutionPlatform.ANDROID,
            ),
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_MAVEN_GOOGLE),
            expected = """root
                |\--- com.google.android.gms:play-services-measurement-api:22.1.0
                |     +--- com.google.android.gms:play-services-ads-identifier:18.0.0
                |     |    \--- com.google.android.gms:play-services-basement:18.0.0 -> 18.4.0
                |     |         +--- androidx.collection:collection:1.0.0 -> 1.1.0
                |     |         |    \--- androidx.annotation:annotation:1.1.0
                |     |         +--- androidx.core:core:1.2.0
                |     |         |    +--- androidx.annotation:annotation:1.1.0
                |     |         |    +--- androidx.lifecycle:lifecycle-runtime:2.0.0 -> 2.1.0
                |     |         |    |    +--- androidx.lifecycle:lifecycle-common:2.1.0
                |     |         |    |    |    \--- androidx.annotation:annotation:1.1.0
                |     |         |    |    +--- androidx.arch.core:core-common:2.1.0
                |     |         |    |    |    \--- androidx.annotation:annotation:1.1.0
                |     |         |    |    \--- androidx.annotation:annotation:1.1.0
                |     |         |    \--- androidx.versionedparcelable:versionedparcelable:1.1.0
                |     |         |         +--- androidx.annotation:annotation:1.1.0
                |     |         |         \--- androidx.collection:collection:1.0.0 -> 1.1.0 (*)
                |     |         \--- androidx.fragment:fragment:1.1.0
                |     |              +--- androidx.annotation:annotation:1.1.0
                |     |              +--- androidx.core:core:1.1.0 -> 1.2.0 (*)
                |     |              +--- androidx.collection:collection:1.1.0 (*)
                |     |              +--- androidx.viewpager:viewpager:1.0.0
                |     |              |    +--- androidx.annotation:annotation:1.0.0 -> 1.1.0
                |     |              |    +--- androidx.core:core:1.0.0 -> 1.2.0 (*)
                |     |              |    \--- androidx.customview:customview:1.0.0
                |     |              |         +--- androidx.annotation:annotation:1.0.0 -> 1.1.0
                |     |              |         \--- androidx.core:core:1.0.0 -> 1.2.0 (*)
                |     |              +--- androidx.loader:loader:1.0.0
                |     |              |    +--- androidx.annotation:annotation:1.0.0 -> 1.1.0
                |     |              |    +--- androidx.core:core:1.0.0 -> 1.2.0 (*)
                |     |              |    +--- androidx.lifecycle:lifecycle-livedata:2.0.0
                |     |              |    |    +--- androidx.arch.core:core-runtime:2.0.0
                |     |              |    |    |    +--- androidx.annotation:annotation:1.0.0 -> 1.1.0
                |     |              |    |    |    \--- androidx.arch.core:core-common:2.0.0 -> 2.1.0 (*)
                |     |              |    |    +--- androidx.lifecycle:lifecycle-livedata-core:2.0.0
                |     |              |    |    |    +--- androidx.lifecycle:lifecycle-common:2.0.0 -> 2.1.0 (*)
                |     |              |    |    |    +--- androidx.arch.core:core-common:2.0.0 -> 2.1.0 (*)
                |     |              |    |    |    \--- androidx.arch.core:core-runtime:2.0.0 (*)
                |     |              |    |    \--- androidx.arch.core:core-common:2.0.0 -> 2.1.0 (*)
                |     |              |    \--- androidx.lifecycle:lifecycle-viewmodel:2.0.0 -> 2.1.0
                |     |              |         \--- androidx.annotation:annotation:1.1.0
                |     |              +--- androidx.activity:activity:1.0.0
                |     |              |    +--- androidx.annotation:annotation:1.1.0
                |     |              |    +--- androidx.core:core:1.1.0 -> 1.2.0 (*)
                |     |              |    +--- androidx.lifecycle:lifecycle-runtime:2.1.0 (*)
                |     |              |    +--- androidx.lifecycle:lifecycle-viewmodel:2.1.0 (*)
                |     |              |    \--- androidx.savedstate:savedstate:1.0.0
                |     |              |         +--- androidx.annotation:annotation:1.1.0
                |     |              |         +--- androidx.arch.core:core-common:2.0.1 -> 2.1.0 (*)
                |     |              |         \--- androidx.lifecycle:lifecycle-common:2.0.0 -> 2.1.0 (*)
                |     |              \--- androidx.lifecycle:lifecycle-viewmodel:2.0.0 -> 2.1.0 (*)
                |     +--- com.google.android.gms:play-services-basement:18.4.0 (*)
                |     +--- com.google.android.gms:play-services-measurement-base:22.1.0
                |     |    \--- com.google.android.gms:play-services-basement:18.4.0 (*)
                |     +--- com.google.android.gms:play-services-measurement-sdk-api:22.1.0
                |     |    +--- com.google.android.gms:play-services-basement:18.4.0 (*)
                |     |    \--- com.google.android.gms:play-services-measurement-base:22.1.0 (*)
                |     +--- com.google.android.gms:play-services-tasks:18.2.0
                |     |    \--- com.google.android.gms:play-services-basement:18.4.0 (*)
                |     +--- com.google.firebase:firebase-common:21.0.0
                |     |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.6.4
                |     |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4
                |     |    |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.4
                |     |    |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.4
                |     |    |    |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.21 -> 1.8.22
                |     |    |    |         |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22
                |     |    |    |         |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22
                |     |    |    |         |    |    \--- org.jetbrains:annotations:13.0
                |     |    |    |         |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22
                |     |    |    |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 (*)
                |     |    |    |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.6.21 -> 1.8.22
                |     |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.4
                |     |    |    +--- com.google.android.gms:play-services-tasks:16.0.1 -> 18.2.0 (*)
                |     |    |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.21 -> 1.8.22 (*)
                |     |    +--- com.google.firebase:firebase-components:18.0.0
                |     |    |    \--- com.google.firebase:firebase-annotations:16.2.0
                |     |    |         \--- javax.inject:javax.inject:1
                |     |    \--- com.google.firebase:firebase-annotations:16.2.0 (*)
                |     +--- com.google.firebase:firebase-common-ktx:21.0.0
                |     |    +--- com.google.firebase:firebase-common:21.0.0 (*)
                |     |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22 (*)
                |     +--- com.google.firebase:firebase-components:18.0.0 (*)
                |     +--- com.google.firebase:firebase-installations:17.0.1
                |     |    +--- com.google.android.gms:play-services-tasks:18.0.1 -> 18.2.0 (*)
                |     |    +--- com.google.firebase:firebase-common:20.1.0 -> 21.0.0 (*)
                |     |    +--- com.google.firebase:firebase-components:17.0.0 -> 18.0.0 (*)
                |     |    \--- com.google.firebase:firebase-installations-interop:17.0.1
                |     |         +--- com.google.android.gms:play-services-tasks:18.0.1 -> 18.2.0 (*)
                |     |         \--- com.google.firebase:firebase-annotations:16.0.0 -> 16.2.0 (*)
                |     +--- com.google.firebase:firebase-installations-interop:17.0.0 -> 17.0.1 (*)
                |     +--- com.google.firebase:firebase-measurement-connector:19.0.0
                |     |    +--- com.google.android.gms:play-services-basement:17.0.0 -> 18.4.0 (*)
                |     |    \--- com.google.firebase:firebase-annotations:16.0.0 -> 16.2.0 (*)
                |     +--- com.google.guava:guava:31.1-android
                |     |    +--- com.google.guava:failureaccess:1.0.1
                |     |    +--- com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava
                |     |    +--- com.google.code.findbugs:jsr305:3.0.2
                |     |    +--- org.checkerframework:checker-qual:3.12.0
                |     |    +--- com.google.errorprone:error_prone_annotations:2.11.0
                |     |    \--- com.google.j2objc:j2objc-annotations:1.3
                |     \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> 1.8.22 (*)
            """.trimMargin()
        )
        runBlocking {
            downloadAndAssertFiles(
                """activity-1.0.0.aar
                |annotation-1.1.0.jar
                |annotations-13.0.jar
                |checker-qual-3.12.0.jar
                |collection-1.1.0.jar
                |core-1.2.0.aar
                |core-common-2.1.0.jar
                |core-runtime-2.0.0.aar
                |customview-1.0.0.aar
                |error_prone_annotations-2.11.0.jar
                |failureaccess-1.0.1.jar
                |firebase-annotations-16.2.0.jar
                |firebase-common-21.0.0.aar
                |firebase-common-ktx-21.0.0.aar
                |firebase-components-18.0.0.aar
                |firebase-installations-17.0.1.aar
                |firebase-installations-interop-17.0.1.aar
                |firebase-measurement-connector-19.0.0.aar
                |fragment-1.1.0.aar
                |guava-31.1-android.jar
                |j2objc-annotations-1.3.jar
                |javax.inject-1.jar
                |jsr305-3.0.2.jar
                |kotlin-stdlib-1.8.22.jar
                |kotlin-stdlib-common-1.8.22.jar
                |kotlin-stdlib-jdk7-1.8.22.jar
                |kotlin-stdlib-jdk8-1.8.22.jar
                |kotlinx-coroutines-core-jvm-1.6.4.jar
                |kotlinx-coroutines-play-services-1.6.4.jar
                |lifecycle-common-2.1.0.jar
                |lifecycle-livedata-2.0.0.aar
                |lifecycle-livedata-core-2.0.0.aar
                |lifecycle-runtime-2.1.0.aar
                |lifecycle-viewmodel-2.1.0.aar
                |listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar
                |loader-1.0.0.aar
                |play-services-ads-identifier-18.0.0.aar
                |play-services-basement-18.4.0.aar
                |play-services-measurement-api-22.1.0.aar
                |play-services-measurement-base-22.1.0.aar
                |play-services-measurement-sdk-api-22.1.0.aar
                |play-services-tasks-18.2.0.aar
                |savedstate-1.0.0.aar
                |versionedparcelable-1.1.0.aar
                |viewpager-1.0.0.aar""".trimMargin(),
                root = root
            )
        }
    }

    /**
     * It checks the following things:
     *  1. Widespread library `firebase-analytics` is imported successfully.
     *  2. Absent dependency on a BOM file in Gradle metadata `.module` file is fallbacked
     *     by the imported BOM in a corresponding `.pom` file
     *     For instance, `dev.gitlive:firebase-analytics-android` declares dependency on `com.google.firebase:firebase-analytics`
     *     without specifying a version in Gradle metadata file, it lacks dependency on a BOM file where dependencies' versions are defined.
     *     Though the version is defined in a BOM file imported in a corresponding '.pom' of `dev.gitlive:firebase-analytics-android`.
     */
    @Test
    fun `dev_gitlive firebase-analytics 2_1_0`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            platform = setOf(
                ResolutionPlatform.ANDROID,
            ),
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_MAVEN_GOOGLE),
            expected = """root
               |\--- dev.gitlive:firebase-analytics:2.1.0
               |     \--- dev.gitlive:firebase-analytics-android:2.1.0
               |          +--- com.google.firebase:firebase-analytics:22.1.0
               |          |    +--- com.google.android.gms:play-services-measurement:22.1.0
               |          |    |    +--- androidx.collection:collection:1.0.0 -> 1.1.0
               |          |    |    |    \--- androidx.annotation:annotation:1.1.0 -> 1.6.0
               |          |    |    |         \--- androidx.annotation:annotation-jvm:1.6.0
               |          |    |    |              \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.0 -> 2.0.20
               |          |    |    |                   +--- org.jetbrains:annotations:13.0 -> 23.0.0
               |          |    |    |                   \--- org.jetbrains.kotlin:kotlin-stdlib-common:2.0.20 (c)
               |          |    |    +--- androidx.legacy:legacy-support-core-utils:1.0.0
               |          |    |    |    +--- androidx.annotation:annotation:1.0.0 -> 1.6.0 (*)
               |          |    |    |    +--- androidx.core:core:1.0.0 -> 1.9.0
               |          |    |    |    |    +--- androidx.annotation:annotation:1.2.0 -> 1.6.0 (*)
               |          |    |    |    |    +--- androidx.annotation:annotation-experimental:1.3.0
               |          |    |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> 2.0.20 (*)
               |          |    |    |    |    +--- androidx.lifecycle:lifecycle-runtime:2.3.1
               |          |    |    |    |    |    +--- androidx.lifecycle:lifecycle-common:2.3.1
               |          |    |    |    |    |    |    \--- androidx.annotation:annotation:1.1.0 -> 1.6.0 (*)
               |          |    |    |    |    |    +--- androidx.arch.core:core-common:2.1.0
               |          |    |    |    |    |    |    \--- androidx.annotation:annotation:1.1.0 -> 1.6.0 (*)
               |          |    |    |    |    |    \--- androidx.annotation:annotation:1.1.0 -> 1.6.0 (*)
               |          |    |    |    |    \--- androidx.versionedparcelable:versionedparcelable:1.1.1
               |          |    |    |    |         +--- androidx.annotation:annotation:1.1.0 -> 1.6.0 (*)
               |          |    |    |    |         \--- androidx.collection:collection:1.0.0 -> 1.1.0 (*)
               |          |    |    |    +--- androidx.documentfile:documentfile:1.0.0
               |          |    |    |    |    \--- androidx.annotation:annotation:1.0.0 -> 1.6.0 (*)
               |          |    |    |    +--- androidx.loader:loader:1.0.0
               |          |    |    |    |    +--- androidx.annotation:annotation:1.0.0 -> 1.6.0 (*)
               |          |    |    |    |    +--- androidx.core:core:1.0.0 -> 1.9.0 (*)
               |          |    |    |    |    +--- androidx.lifecycle:lifecycle-livedata:2.0.0
               |          |    |    |    |    |    +--- androidx.arch.core:core-runtime:2.0.0
               |          |    |    |    |    |    |    +--- androidx.annotation:annotation:1.0.0 -> 1.6.0 (*)
               |          |    |    |    |    |    |    \--- androidx.arch.core:core-common:2.0.0 -> 2.1.0 (*)
               |          |    |    |    |    |    +--- androidx.lifecycle:lifecycle-livedata-core:2.0.0
               |          |    |    |    |    |    |    +--- androidx.lifecycle:lifecycle-common:2.0.0 -> 2.3.1 (*)
               |          |    |    |    |    |    |    +--- androidx.arch.core:core-common:2.0.0 -> 2.1.0 (*)
               |          |    |    |    |    |    |    \--- androidx.arch.core:core-runtime:2.0.0 (*)
               |          |    |    |    |    |    \--- androidx.arch.core:core-common:2.0.0 -> 2.1.0 (*)
               |          |    |    |    |    \--- androidx.lifecycle:lifecycle-viewmodel:2.0.0 -> 2.1.0
               |          |    |    |    |         \--- androidx.annotation:annotation:1.1.0 -> 1.6.0 (*)
               |          |    |    |    +--- androidx.localbroadcastmanager:localbroadcastmanager:1.0.0
               |          |    |    |    |    \--- androidx.annotation:annotation:1.0.0 -> 1.6.0 (*)
               |          |    |    |    \--- androidx.print:print:1.0.0
               |          |    |    |         \--- androidx.annotation:annotation:1.0.0 -> 1.6.0 (*)
               |          |    |    +--- com.google.android.gms:play-services-ads-identifier:18.0.0
               |          |    |    |    \--- com.google.android.gms:play-services-basement:18.0.0 -> 18.4.0
               |          |    |    |         +--- androidx.collection:collection:1.0.0 -> 1.1.0 (*)
               |          |    |    |         +--- androidx.core:core:1.2.0 -> 1.9.0 (*)
               |          |    |    |         \--- androidx.fragment:fragment:1.1.0
               |          |    |    |              +--- androidx.annotation:annotation:1.1.0 -> 1.6.0 (*)
               |          |    |    |              +--- androidx.core:core:1.1.0 -> 1.9.0 (*)
               |          |    |    |              +--- androidx.collection:collection:1.1.0 (*)
               |          |    |    |              +--- androidx.viewpager:viewpager:1.0.0
               |          |    |    |              |    +--- androidx.annotation:annotation:1.0.0 -> 1.6.0 (*)
               |          |    |    |              |    +--- androidx.core:core:1.0.0 -> 1.9.0 (*)
               |          |    |    |              |    \--- androidx.customview:customview:1.0.0
               |          |    |    |              |         +--- androidx.annotation:annotation:1.0.0 -> 1.6.0 (*)
               |          |    |    |              |         \--- androidx.core:core:1.0.0 -> 1.9.0 (*)
               |          |    |    |              +--- androidx.loader:loader:1.0.0 (*)
               |          |    |    |              +--- androidx.activity:activity:1.0.0
               |          |    |    |              |    +--- androidx.annotation:annotation:1.1.0 -> 1.6.0 (*)
               |          |    |    |              |    +--- androidx.core:core:1.1.0 -> 1.9.0 (*)
               |          |    |    |              |    +--- androidx.lifecycle:lifecycle-runtime:2.1.0 -> 2.3.1 (*)
               |          |    |    |              |    +--- androidx.lifecycle:lifecycle-viewmodel:2.1.0 (*)
               |          |    |    |              |    \--- androidx.savedstate:savedstate:1.0.0
               |          |    |    |              |         +--- androidx.annotation:annotation:1.1.0 -> 1.6.0 (*)
               |          |    |    |              |         +--- androidx.arch.core:core-common:2.0.1 -> 2.1.0 (*)
               |          |    |    |              |         \--- androidx.lifecycle:lifecycle-common:2.0.0 -> 2.3.1 (*)
               |          |    |    |              \--- androidx.lifecycle:lifecycle-viewmodel:2.0.0 -> 2.1.0 (*)
               |          |    |    +--- com.google.android.gms:play-services-basement:18.4.0 (*)
               |          |    |    +--- com.google.android.gms:play-services-measurement-base:22.1.0
               |          |    |    |    \--- com.google.android.gms:play-services-basement:18.4.0 (*)
               |          |    |    +--- com.google.android.gms:play-services-measurement-impl:22.1.0
               |          |    |    |    +--- androidx.collection:collection:1.0.0 -> 1.1.0 (*)
               |          |    |    |    +--- androidx.core:core:1.9.0 (*)
               |          |    |    |    +--- androidx.privacysandbox.ads:ads-adservices:1.0.0-beta05
               |          |    |    |    |    +--- androidx.annotation:annotation:1.6.0 (*)
               |          |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 -> 2.0.20 (*)
               |          |    |    |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1
               |          |    |    |    |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.1
               |          |    |    |    |              +--- org.jetbrains:annotations:23.0.0
               |          |    |    |    |              +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.7.1
               |          |    |    |    |              |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.1 (c)
               |          |    |    |    |              +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.20 -> 2.0.20
               |          |    |    |    |              |    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.20 (*)
               |          |    |    |    |              \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.20 -> 1.8.22
               |          |    |    |    |                   +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.20 (*)
               |          |    |    |    |                   \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22
               |          |    |    |    |                        \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.20 (*)
               |          |    |    |    +--- androidx.privacysandbox.ads:ads-adservices-java:1.0.0-beta05
               |          |    |    |    |    +--- androidx.annotation:annotation:1.2.0 -> 1.6.0 (*)
               |          |    |    |    |    +--- com.google.guava:listenablefuture:1.0 -> 9999.0-empty-to-avoid-conflict-with-guava
               |          |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 -> 2.0.20 (*)
               |          |    |    |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1 (*)
               |          |    |    |    +--- com.google.android.gms:play-services-ads-identifier:18.0.0 (*)
               |          |    |    |    +--- com.google.android.gms:play-services-basement:18.4.0 (*)
               |          |    |    |    +--- com.google.android.gms:play-services-measurement-base:22.1.0 (*)
               |          |    |    |    +--- com.google.android.gms:play-services-stats:17.0.2
               |          |    |    |    |    +--- androidx.legacy:legacy-support-core-utils:1.0.0 (*)
               |          |    |    |    |    \--- com.google.android.gms:play-services-basement:18.0.0 -> 18.4.0 (*)
               |          |    |    |    \--- com.google.guava:guava:31.1-android
               |          |    |    |         +--- com.google.guava:failureaccess:1.0.1
               |          |    |    |         +--- com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava
               |          |    |    |         +--- com.google.code.findbugs:jsr305:3.0.2
               |          |    |    |         +--- org.checkerframework:checker-qual:3.12.0
               |          |    |    |         +--- com.google.errorprone:error_prone_annotations:2.11.0
               |          |    |    |         \--- com.google.j2objc:j2objc-annotations:1.3
               |          |    |    \--- com.google.android.gms:play-services-stats:17.0.2 (*)
               |          |    +--- com.google.android.gms:play-services-measurement-api:22.1.0
               |          |    |    +--- com.google.android.gms:play-services-ads-identifier:18.0.0 (*)
               |          |    |    +--- com.google.android.gms:play-services-basement:18.4.0 (*)
               |          |    |    +--- com.google.android.gms:play-services-measurement-base:22.1.0 (*)
               |          |    |    +--- com.google.android.gms:play-services-measurement-sdk-api:22.1.0
               |          |    |    |    +--- com.google.android.gms:play-services-basement:18.4.0 (*)
               |          |    |    |    \--- com.google.android.gms:play-services-measurement-base:22.1.0 (*)
               |          |    |    +--- com.google.android.gms:play-services-tasks:18.2.0
               |          |    |    |    \--- com.google.android.gms:play-services-basement:18.4.0 (*)
               |          |    |    +--- com.google.firebase:firebase-common:21.0.0
               |          |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.6.4 -> 1.7.1
               |          |    |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1 (*)
               |          |    |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.7.1
               |          |    |    |    |    +--- com.google.android.gms:play-services-tasks:16.0.1 -> 18.2.0 (*)
               |          |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.20 -> 1.8.22 (*)
               |          |    |    |    +--- com.google.firebase:firebase-components:18.0.0
               |          |    |    |    |    \--- com.google.firebase:firebase-annotations:16.2.0
               |          |    |    |    |         \--- javax.inject:javax.inject:1
               |          |    |    |    \--- com.google.firebase:firebase-annotations:16.2.0 (*)
               |          |    |    +--- com.google.firebase:firebase-common-ktx:21.0.0
               |          |    |    |    +--- com.google.firebase:firebase-common:21.0.0 (*)
               |          |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22 (*)
               |          |    |    +--- com.google.firebase:firebase-components:18.0.0 (*)
               |          |    |    +--- com.google.firebase:firebase-installations:17.0.1
               |          |    |    |    +--- com.google.android.gms:play-services-tasks:18.0.1 -> 18.2.0 (*)
               |          |    |    |    +--- com.google.firebase:firebase-common:20.1.0 -> 21.0.0 (*)
               |          |    |    |    +--- com.google.firebase:firebase-components:17.0.0 -> 18.0.0 (*)
               |          |    |    |    \--- com.google.firebase:firebase-installations-interop:17.0.1
               |          |    |    |         +--- com.google.android.gms:play-services-tasks:18.0.1 -> 18.2.0 (*)
               |          |    |    |         \--- com.google.firebase:firebase-annotations:16.0.0 -> 16.2.0 (*)
               |          |    |    +--- com.google.firebase:firebase-installations-interop:17.0.0 -> 17.0.1 (*)
               |          |    |    +--- com.google.firebase:firebase-measurement-connector:19.0.0
               |          |    |    |    +--- com.google.android.gms:play-services-basement:17.0.0 -> 18.4.0 (*)
               |          |    |    |    \--- com.google.firebase:firebase-annotations:16.0.0 -> 16.2.0 (*)
               |          |    |    +--- com.google.guava:guava:31.1-android (*)
               |          |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> 2.0.20 (*)
               |          |    \--- com.google.android.gms:play-services-measurement-sdk:22.1.0
               |          |         +--- androidx.collection:collection:1.0.0 -> 1.1.0 (*)
               |          |         +--- com.google.android.gms:play-services-basement:18.4.0 (*)
               |          |         +--- com.google.android.gms:play-services-measurement-base:22.1.0 (*)
               |          |         \--- com.google.android.gms:play-services-measurement-impl:22.1.0 (*)
               |          +--- dev.gitlive:firebase-app:2.1.0
               |          |    \--- dev.gitlive:firebase-app-android:2.1.0
               |          |         +--- com.google.firebase:firebase-common-ktx:21.0.0 (*)
               |          |         \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.20 (*)
               |          \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.20 (*)
            """.trimMargin()
        )
        runBlocking {
            downloadAndAssertFiles(
                """activity-1.0.0.aar
               |ads-adservices-1.0.0-beta05.aar
               |ads-adservices-java-1.0.0-beta05.aar
               |annotation-experimental-1.3.0.aar
               |annotation-jvm-1.6.0.jar
               |annotations-23.0.0.jar
               |checker-qual-3.12.0.jar
               |collection-1.1.0.jar
               |core-1.9.0.aar
               |core-common-2.1.0.jar
               |core-runtime-2.0.0.aar
               |customview-1.0.0.aar
               |documentfile-1.0.0.aar
               |error_prone_annotations-2.11.0.jar
               |failureaccess-1.0.1.jar
               |firebase-analytics-22.1.0.aar
               |firebase-analytics-android-2.1.0.aar
               |firebase-annotations-16.2.0.jar
               |firebase-app-android-2.1.0.aar
               |firebase-common-21.0.0.aar
               |firebase-common-ktx-21.0.0.aar
               |firebase-components-18.0.0.aar
               |firebase-installations-17.0.1.aar
               |firebase-installations-interop-17.0.1.aar
               |firebase-measurement-connector-19.0.0.aar
               |fragment-1.1.0.aar
               |guava-31.1-android.jar
               |j2objc-annotations-1.3.jar
               |javax.inject-1.jar
               |jsr305-3.0.2.jar
               |kotlin-stdlib-2.0.20.jar
               |kotlin-stdlib-jdk7-1.8.22.jar
               |kotlin-stdlib-jdk8-1.8.22.jar
               |kotlinx-coroutines-core-jvm-1.7.1.jar
               |kotlinx-coroutines-play-services-1.7.1.jar
               |legacy-support-core-utils-1.0.0.aar
               |lifecycle-common-2.3.1.jar
               |lifecycle-livedata-2.0.0.aar
               |lifecycle-livedata-core-2.0.0.aar
               |lifecycle-runtime-2.3.1.aar
               |lifecycle-viewmodel-2.1.0.aar
               |listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar
               |loader-1.0.0.aar
               |localbroadcastmanager-1.0.0.aar
               |play-services-ads-identifier-18.0.0.aar
               |play-services-basement-18.4.0.aar
               |play-services-measurement-22.1.0.aar
               |play-services-measurement-api-22.1.0.aar
               |play-services-measurement-base-22.1.0.aar
               |play-services-measurement-impl-22.1.0.aar
               |play-services-measurement-sdk-22.1.0.aar
               |play-services-measurement-sdk-api-22.1.0.aar
               |play-services-stats-17.0.2.aar
               |play-services-tasks-18.2.0.aar
               |print-1.0.0.aar
               |savedstate-1.0.0.aar
               |versionedparcelable-1.1.1.aar
               |viewpager-1.0.0.aar""".trimMargin(),
                root = root
            )
        }
    }

    @Test
    fun `dev_gitlive firebase-crashlytics 2_1_0`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_MAVEN_GOOGLE),
            platform = setOf(
                ResolutionPlatform.ANDROID,
            )
        )
        runBlocking {
            downloadAndAssertFiles(
                """activity-1.0.0.aar
                |annotation-1.1.0.jar
                |annotations-13.0.jar
                |collection-1.1.0.jar
                |core-1.2.0.aar
                |core-common-2.1.0.jar
                |core-runtime-2.0.0.aar
                |customview-1.0.0.aar
                |firebase-annotations-16.2.0.jar
                |firebase-app-android-2.1.0.aar
                |firebase-common-21.0.0.aar
                |firebase-common-ktx-21.0.0.aar
                |firebase-components-18.0.0.aar
                |firebase-config-interop-16.0.1.aar
                |firebase-crashlytics-19.0.3.aar
                |firebase-crashlytics-android-2.1.0.aar
                |firebase-crashlytics-ktx-19.0.3.aar
                |firebase-encoders-17.0.0.jar
                |firebase-encoders-json-18.0.1.aar
                |firebase-installations-18.0.0.aar
                |firebase-installations-interop-17.2.0.aar
                |firebase-measurement-connector-20.0.1.aar
                |firebase-sessions-2.0.3.aar
                |fragment-1.1.0.aar
                |javax.inject-1.jar
                |kotlin-stdlib-2.0.20.jar
                |kotlin-stdlib-jdk7-1.8.22.jar
                |kotlin-stdlib-jdk8-1.8.22.jar
                |kotlinx-coroutines-core-jvm-1.6.4.jar
                |kotlinx-coroutines-play-services-1.6.4.jar
                |lifecycle-common-2.1.0.jar
                |lifecycle-livedata-2.0.0.aar
                |lifecycle-livedata-core-2.0.0.aar
                |lifecycle-runtime-2.1.0.aar
                |lifecycle-viewmodel-2.1.0.aar
                |loader-1.0.0.aar
                |play-services-basement-18.3.0.aar
                |play-services-tasks-18.1.0.aar
                |savedstate-1.0.0.aar
                |versionedparcelable-1.1.0.aar
                |viewpager-1.0.0.aar""".trimMargin(),
                root = root
            )
        }
    }

    @Test
    fun `org_jetbrains_kotlin kotlin-test-annotations-common 1_9_0`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            platform = setOf(
                ResolutionPlatform.IOS_X64,
                ResolutionPlatform.IOS_ARM64,
                ResolutionPlatform.IOS_SIMULATOR_ARM64
            ),
            expected = """root
                |\--- org.jetbrains.kotlin:kotlin-test-annotations-common:1.9.0
                |     \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.0
            """.trimMargin()
        )
        assertFiles(
            """kotlin-stdlib-common-1.9.0.jar
                |kotlin-test-annotations-common-1.9.0.jar""".trimMargin(),
            root
        )
    }

    @Test
    fun `org_jetbrains_kotlinx atomicfu 0_23_2`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            expected = """root
                |\--- org.jetbrains.kotlinx:atomicfu:0.23.2
                |     \--- org.jetbrains.kotlinx:atomicfu-jvm:0.23.2
            """.trimMargin()
        )
        assertFiles(
            """atomicfu-jvm-0.23.2.jar""".trimMargin(),
            root
        )

        root.distinctBfsSequence().forEach {
            assertTrue(
                it.messages.none { it.severity == Severity.ERROR },
                "There should be no messages for $it: ${it.messages.filter { it.severity == Severity.ERROR }}"
            )
        }

    }

    @Test
    fun `org_jetbrains_kotlin kotlin-test 1_9_20`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            scope = ResolutionScope.RUNTIME,
            expected = """root
                |\--- org.jetbrains.kotlin:kotlin-test:1.9.20
                |     \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.20
                |          \--- org.jetbrains:annotations:13.0
            """.trimMargin()
        )
        assertFiles(
            """annotations-13.0-sources.jar
                |annotations-13.0.jar
                |kotlin-stdlib-1.9.20-sources.jar
                |kotlin-stdlib-1.9.20.jar
                |kotlin-test-1.9.20-sources.jar
                |kotlin-test-1.9.20.jar""".trimMargin(),
            root, true
        )
    }

    @Test
    fun `org_jetbrains_compose_runtime runtime-saveable-desktop 1_5_10`(testInfo: TestInfo) {
        doTest(
            testInfo,
            scope = ResolutionScope.RUNTIME,
            expected = """root
                |\--- org.jetbrains.compose.runtime:runtime-saveable-desktop:1.5.10
                |     +--- org.jetbrains.compose.runtime:runtime:1.5.10
                |     |    \--- org.jetbrains.compose.runtime:runtime-desktop:1.5.10
                |     |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21
                |     |         |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                |     |         |    \--- org.jetbrains:annotations:13.0
                |     |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                |     |         +--- org.jetbrains.kotlinx:atomicfu:0.17.0
                |     |         |    \--- org.jetbrains.kotlinx:atomicfu-jvm:0.17.0
                |     |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.6.0 -> 1.8.21 (*)
                |     |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.6.0 -> 1.8.21
                |     |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4
                |     |              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.4
                |     |                   +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.4
                |     |                   +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.21
                |     |                   |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.6.21 -> 1.8.21 (*)
                |     |                   |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.6.21
                |     |                   |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.6.21 -> 1.8.21 (*)
                |     |                   \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.6.21 -> 1.8.21
                |     \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
            """.trimMargin()
        )
    }

    @Test
    fun `org_jetbrains_kotlinx kotlinx-coroutines-core 1_6_4`(testInfo: TestInfo) {
        doTest(
            testInfo,
            expected = """root
                |\--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4
                |     \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.4
                |          +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.4
                |          +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.21
                |          |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.6.21
                |          |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.6.21
                |          |    |    \--- org.jetbrains:annotations:13.0
                |          |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.6.21
                |          |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.6.21 (*)
                |          \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.6.21
            """.trimMargin()
        )
    }

    @Test
    fun `org_jetbrains_skiko skiko 0_7_85`(testInfo: TestInfo) {
        doTest(
            testInfo,
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_COMPOSE_DEV),
            expected = """root
                |\--- org.jetbrains.skiko:skiko:0.7.85
                |     \--- org.jetbrains.skiko:skiko-awt:0.7.85
                |          \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.20
                |               +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.20
                |               |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.20
                |               |    \--- org.jetbrains:annotations:13.0
                |               \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.20
                |                    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.20 (*)
            """.trimMargin()
        )
    }

    @Test
    fun `org_jetbrains_compose_desktop desktop-jvm-macos-arm64 1_5_10`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_COMPOSE_DEV),
            expected = """root
                |\--- org.jetbrains.compose.desktop:desktop-jvm-macos-arm64:1.5.10
                |     \--- org.jetbrains.compose.desktop:desktop:1.5.10
                |          \--- org.jetbrains.compose.desktop:desktop-jvm:1.5.10
                |               +--- org.jetbrains.compose.foundation:foundation:1.5.10
                |               |    \--- org.jetbrains.compose.foundation:foundation-desktop:1.5.10
                |               |         +--- org.jetbrains.compose.animation:animation:1.5.10
                |               |         |    \--- org.jetbrains.compose.animation:animation-desktop:1.5.10
                |               |         |         +--- org.jetbrains.compose.animation:animation-core:1.5.10
                |               |         |         |    \--- org.jetbrains.compose.animation:animation-core-desktop:1.5.10
                |               |         |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4
                |               |         |         |              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.4
                |               |         |         |                   +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.4
                |               |         |         |                   +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.21 -> 1.8.20
                |               |         |         |                   |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.20
                |               |         |         |                   |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.20
                |               |         |         |                   |    |    \--- org.jetbrains:annotations:13.0
                |               |         |         |                   |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.20
                |               |         |         |                   |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.20 (*)
                |               |         |         |                   \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.6.21 -> 1.8.20
                |               |         |         +--- org.jetbrains.compose.foundation:foundation-layout:1.5.10
                |               |         |         |    \--- org.jetbrains.compose.foundation:foundation-layout-desktop:1.5.10
                |               |         |         |         \--- org.jetbrains.compose.ui:ui:1.5.10
                |               |         |         |              \--- org.jetbrains.compose.ui:ui-desktop:1.5.10
                |               |         |         |                   +--- org.jetbrains.compose.runtime:runtime-saveable:1.5.10
                |               |         |         |                   |    \--- org.jetbrains.compose.runtime:runtime-saveable-desktop:1.5.10
                |               |         |         |                   |         \--- org.jetbrains.compose.runtime:runtime:1.5.10
                |               |         |         |                   |              \--- org.jetbrains.compose.runtime:runtime-desktop:1.5.10
                |               |         |         |                   |                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4 (*)
                |               |         |         |                   +--- org.jetbrains.compose.ui:ui-geometry:1.5.10
                |               |         |         |                   |    \--- org.jetbrains.compose.ui:ui-geometry-desktop:1.5.10
                |               |         |         |                   +--- org.jetbrains.compose.ui:ui-graphics:1.5.10
                |               |         |         |                   |    \--- org.jetbrains.compose.ui:ui-graphics-desktop:1.5.10
                |               |         |         |                   |         +--- org.jetbrains.compose.ui:ui-unit:1.5.10
                |               |         |         |                   |         |    \--- org.jetbrains.compose.ui:ui-unit-desktop:1.5.10
                |               |         |         |                   |         |         \--- org.jetbrains.compose.ui:ui-geometry:1.5.10 (*)
                |               |         |         |                   |         \--- org.jetbrains.skiko:skiko:0.7.85
                |               |         |         |                   |              \--- org.jetbrains.skiko:skiko-awt:0.7.85
                |               |         |         |                   |                   \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.20 (*)
                |               |         |         |                   +--- org.jetbrains.compose.ui:ui-text:1.5.10
                |               |         |         |                   |    \--- org.jetbrains.compose.ui:ui-text-desktop:1.5.10
                |               |         |         |                   |         +--- org.jetbrains.compose.ui:ui-graphics:1.5.10 (*)
                |               |         |         |                   |         +--- org.jetbrains.compose.ui:ui-unit:1.5.10 (*)
                |               |         |         |                   |         \--- org.jetbrains.skiko:skiko:0.7.85 (*)
                |               |         |         |                   +--- org.jetbrains.compose.ui:ui-unit:1.5.10 (*)
                |               |         |         |                   \--- org.jetbrains.skiko:skiko:0.7.85 (*)
                |               |         |         +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                |               |         |         +--- org.jetbrains.compose.ui:ui:1.5.10 (*)
                |               |         |         \--- org.jetbrains.compose.ui:ui-geometry:1.5.10 (*)
                |               |         +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                |               |         +--- org.jetbrains.compose.ui:ui:1.5.10 (*)
                |               |         \--- org.jetbrains.skiko:skiko:0.7.85 (*)
                |               +--- org.jetbrains.compose.material:material:1.5.10
                |               |    \--- org.jetbrains.compose.material:material-desktop:1.5.10
                |               |         +--- org.jetbrains.compose.animation:animation-core:1.5.10 (*)
                |               |         +--- org.jetbrains.compose.foundation:foundation:1.5.10 (*)
                |               |         +--- org.jetbrains.compose.material:material-icons-core:1.5.10
                |               |         |    \--- org.jetbrains.compose.material:material-icons-core-desktop:1.5.10
                |               |         |         \--- org.jetbrains.compose.ui:ui:1.5.10 (*)
                |               |         +--- org.jetbrains.compose.material:material-ripple:1.5.10
                |               |         |    \--- org.jetbrains.compose.material:material-ripple-desktop:1.5.10
                |               |         |         +--- org.jetbrains.compose.foundation:foundation:1.5.10 (*)
                |               |         |         \--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                |               |         +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                |               |         +--- org.jetbrains.compose.ui:ui:1.5.10 (*)
                |               |         \--- org.jetbrains.compose.ui:ui-text:1.5.10 (*)
                |               +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                |               +--- org.jetbrains.compose.ui:ui:1.5.10 (*)
                |               \--- org.jetbrains.compose.ui:ui-tooling-preview:1.5.10
                |                    \--- org.jetbrains.compose.ui:ui-tooling-preview-desktop:1.5.10
                |                         \--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
            """.trimMargin()
        )

        root.distinctBfsSequence()
            .filterIsInstance<MavenDependencyNode>()
            .flatMap { it.dependency.files() }
            .mapNotNull { runBlocking { it.getPath() } }
            .forEach {
                assertTrue(it.extension == "jar", "Only jar files are expected, got ${it.name}")
            }
    }

    @Test
    fun `androidx_annotation annotation 1_6_0`(testInfo: TestInfo) {
        doTest(
            testInfo,
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_MAVEN_GOOGLE),
            expected = """root
                |\--- androidx.annotation:annotation:1.6.0
                |     \--- androidx.annotation:annotation-jvm:1.6.0
                |          \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.0
                |               +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.0
                |               \--- org.jetbrains:annotations:13.0
            """.trimMargin()
        )
    }

    @Test
    fun `org_jetbrains_compose_foundation foundation 1_6_10`(testInfo: TestInfo) {
        doTest(
            testInfo,
            platform = setOf(ResolutionPlatform.ANDROID),
            scope = ResolutionScope.RUNTIME,
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_MAVEN_GOOGLE),
            expected = """root
                |\--- org.jetbrains.compose.foundation:foundation:1.6.10
                |     \--- androidx.compose.foundation:foundation:1.6.7
                |          \--- androidx.compose.foundation:foundation-android:1.6.7
                |               +--- androidx.annotation:annotation:1.1.0 -> 1.7.0
                |               |    \--- androidx.annotation:annotation-jvm:1.7.0
                |               |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> 1.8.22
                |               |              +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22
                |               |              \--- org.jetbrains:annotations:13.0 -> 23.0.0
                |               +--- androidx.collection:collection:1.4.0
                |               |    \--- androidx.collection:collection-jvm:1.4.0
                |               |         +--- androidx.annotation:annotation:1.7.0 (*)
                |               |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 (*)
                |               |         \--- androidx.collection:collection-ktx:1.4.0 (c)
                |               +--- androidx.compose.animation:animation:1.6.7
                |               |    \--- androidx.compose.animation:animation-android:1.6.7
                |               |         +--- androidx.annotation:annotation:1.1.0 -> 1.7.0 (*)
                |               |         +--- androidx.compose.animation:animation-core:1.6.7
                |               |         |    \--- androidx.compose.animation:animation-core-android:1.6.7
                |               |         |         +--- androidx.annotation:annotation:1.1.0 -> 1.7.0 (*)
                |               |         |         +--- androidx.collection:collection:1.4.0 (*)
                |               |         |         +--- androidx.compose.runtime:runtime:1.6.7
                |               |         |         |    \--- androidx.compose.runtime:runtime-android:1.6.7
                |               |         |         |         +--- androidx.collection:collection:1.4.0 (*)
                |               |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 (*)
                |               |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22
                |               |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1
                |               |         |         |         |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1
                |               |         |         |         |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.1
                |               |         |         |         |    |         +--- org.jetbrains:annotations:23.0.0
                |               |         |         |         |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.7.1
                |               |         |         |         |    |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.20 -> 1.8.22
                |               |         |         |         |    |         \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.20
                |               |         |         |         |    |              +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.20 -> 1.8.22 (*)
                |               |         |         |         |    |              \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.20
                |               |         |         |         |    |                   \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.20 -> 1.8.22 (*)
                |               |         |         |         |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.7.1
                |               |         |         |         |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.20 (*)
                |               |         |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1 (*)
                |               |         |         +--- androidx.compose.ui:ui:1.6.7
                |               |         |         |    \--- androidx.compose.ui:ui-android:1.6.7
                |               |         |         |         +--- androidx.activity:activity-ktx:1.7.0
                |               |         |         |         |    +--- androidx.activity:activity:1.7.0
                |               |         |         |         |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.7.0 (*)
                |               |         |         |         |    |    +--- androidx.collection:collection:1.0.0 -> 1.4.0 (*)
                |               |         |         |         |    |    +--- androidx.core:core:1.8.0 -> 1.12.0
                |               |         |         |         |    |    |    +--- androidx.annotation:annotation:1.6.0 -> 1.7.0 (*)
                |               |         |         |         |    |    |    +--- androidx.annotation:annotation-experimental:1.3.0
                |               |         |         |         |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> 1.8.22 (*)
                |               |         |         |         |    |    |    +--- androidx.collection:collection:1.0.0 -> 1.4.0 (*)
                |               |         |         |         |    |    |    +--- androidx.concurrent:concurrent-futures:1.0.0 -> 1.1.0
                |               |         |         |         |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.7.0 (*)
                |               |         |         |         |    |    |    |    \--- com.google.guava:listenablefuture:1.0
                |               |         |         |         |    |    |    +--- androidx.interpolator:interpolator:1.0.0
                |               |         |         |         |    |    |    |    \--- androidx.annotation:annotation:1.0.0 -> 1.7.0 (*)
                |               |         |         |         |    |    |    +--- androidx.lifecycle:lifecycle-runtime:2.3.1 -> 2.6.1
                |               |         |         |         |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.7.0 (*)
                |               |         |         |         |    |    |    |    +--- androidx.arch.core:core-common:2.2.0
                |               |         |         |         |    |    |    |    |    \--- androidx.annotation:annotation:1.1.0 -> 1.7.0 (*)
                |               |         |         |         |    |    |    |    +--- androidx.arch.core:core-runtime:2.2.0
                |               |         |         |         |    |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.7.0 (*)
                |               |         |         |         |    |    |    |    |    \--- androidx.arch.core:core-common:2.2.0 (*)
                |               |         |         |         |    |    |    |    +--- androidx.lifecycle:lifecycle-common:2.6.1
                |               |         |         |         |    |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.7.0 (*)
                |               |         |         |         |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> 1.8.22 (*)
                |               |         |         |         |    |    |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4 -> 1.7.1 (*)
                |               |         |         |         |    |    |    |    |    \--- androidx.lifecycle:lifecycle-process:2.6.1 (c)
                |               |         |         |         |    |    |    |    +--- androidx.profileinstaller:profileinstaller:1.3.0
                |               |         |         |         |    |    |    |    |    +--- androidx.annotation:annotation:1.2.0 -> 1.7.0 (*)
                |               |         |         |         |    |    |    |    |    +--- androidx.concurrent:concurrent-futures:1.1.0 (*)
                |               |         |         |         |    |    |    |    |    +--- androidx.startup:startup-runtime:1.1.1
                |               |         |         |         |    |    |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.7.0 (*)
                |               |         |         |         |    |    |    |    |    |    \--- androidx.tracing:tracing:1.0.0
                |               |         |         |         |    |    |    |    |    |         \--- androidx.annotation:annotation:1.1.0 -> 1.7.0 (*)
                |               |         |         |         |    |    |    |    |    \--- com.google.guava:listenablefuture:1.0
                |               |         |         |         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> 1.8.22 (*)
                |               |         |         |         |    |    |    |    \--- androidx.lifecycle:lifecycle-process:2.6.1 (c)
                |               |         |         |         |    |    |    +--- androidx.versionedparcelable:versionedparcelable:1.1.1
                |               |         |         |         |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.7.0 (*)
                |               |         |         |         |    |    |    |    \--- androidx.collection:collection:1.0.0 -> 1.4.0 (*)
                |               |         |         |         |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 (*)
                |               |         |         |         |    |    |    \--- androidx.core:core-ktx:1.12.0 (c)
                |               |         |         |         |    |    +--- androidx.lifecycle:lifecycle-runtime:2.6.1 (*)
                |               |         |         |         |    |    +--- androidx.lifecycle:lifecycle-viewmodel:2.6.1
                |               |         |         |         |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.7.0 (*)
                |               |         |         |         |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> 1.8.22 (*)
                |               |         |         |         |    |    |    \--- androidx.lifecycle:lifecycle-process:2.6.1 (c)
                |               |         |         |         |    |    +--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.6.1
                |               |         |         |         |    |    |    +--- androidx.annotation:annotation:1.0.0 -> 1.7.0 (*)
                |               |         |         |         |    |    |    +--- androidx.core:core-ktx:1.2.0 -> 1.12.0
                |               |         |         |         |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.7.0 (*)
                |               |         |         |         |    |    |    |    +--- androidx.core:core:1.12.0 (*)
                |               |         |         |         |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 (*)
                |               |         |         |         |    |    |    +--- androidx.lifecycle:lifecycle-livedata-core:2.6.1
                |               |         |         |         |    |    |    |    +--- androidx.arch.core:core-common:2.1.0 -> 2.2.0 (*)
                |               |         |         |         |    |    |    |    +--- androidx.arch.core:core-runtime:2.1.0 -> 2.2.0 (*)
                |               |         |         |         |    |    |    |    +--- androidx.lifecycle:lifecycle-common:2.6.1 (*)
                |               |         |         |         |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> 1.8.22 (*)
                |               |         |         |         |    |    |    |    \--- androidx.lifecycle:lifecycle-process:2.6.1 (c)
                |               |         |         |         |    |    |    +--- androidx.lifecycle:lifecycle-viewmodel:2.6.1 (*)
                |               |         |         |         |    |    |    +--- androidx.savedstate:savedstate:1.2.1
                |               |         |         |         |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.7.0 (*)
                |               |         |         |         |    |    |    |    +--- androidx.arch.core:core-common:2.1.0 -> 2.2.0 (*)
                |               |         |         |         |    |    |    |    +--- androidx.lifecycle:lifecycle-common:2.6.1 (*)
                |               |         |         |         |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> 1.8.22 (*)
                |               |         |         |         |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> 1.8.22 (*)
                |               |         |         |         |    |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4 -> 1.7.1 (*)
                |               |         |         |         |    |    |    \--- androidx.lifecycle:lifecycle-process:2.6.1 (c)
                |               |         |         |         |    |    +--- androidx.profileinstaller:profileinstaller:1.3.0 (*)
                |               |         |         |         |    |    +--- androidx.savedstate:savedstate:1.2.1 (*)
                |               |         |         |         |    |    +--- androidx.tracing:tracing:1.0.0 (*)
                |               |         |         |         |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> 1.8.22 (*)
                |               |         |         |         |    +--- androidx.core:core-ktx:1.1.0 -> 1.12.0 (*)
                |               |         |         |         |    +--- androidx.lifecycle:lifecycle-runtime-ktx:2.6.1
                |               |         |         |         |    |    +--- androidx.annotation:annotation:1.0.0 -> 1.7.0 (*)
                |               |         |         |         |    |    +--- androidx.lifecycle:lifecycle-runtime:2.6.1 (*)
                |               |         |         |         |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> 1.8.22 (*)
                |               |         |         |         |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4 -> 1.7.1 (*)
                |               |         |         |         |    |    \--- androidx.lifecycle:lifecycle-process:2.6.1 (c)
                |               |         |         |         |    +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1
                |               |         |         |         |    |    +--- androidx.lifecycle:lifecycle-viewmodel:2.6.1 (*)
                |               |         |         |         |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> 1.8.22 (*)
                |               |         |         |         |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4 -> 1.7.1 (*)
                |               |         |         |         |    |    \--- androidx.lifecycle:lifecycle-process:2.6.1 (c)
                |               |         |         |         |    +--- androidx.savedstate:savedstate-ktx:1.2.1
                |               |         |         |         |    |    +--- androidx.savedstate:savedstate:1.2.1 (*)
                |               |         |         |         |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> 1.8.22 (*)
                |               |         |         |         |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> 1.8.22 (*)
                |               |         |         |         +--- androidx.annotation:annotation:1.6.0 -> 1.7.0 (*)
                |               |         |         |         +--- androidx.autofill:autofill:1.0.0
                |               |         |         |         |    \--- androidx.core:core:1.1.0 -> 1.12.0 (*)
                |               |         |         |         +--- androidx.collection:collection:1.0.0 -> 1.4.0 (*)
                |               |         |         |         +--- androidx.collection:collection:1.4.0 (*)
                |               |         |         |         +--- androidx.compose.runtime:runtime:1.6.7 (*)
                |               |         |         |         +--- androidx.compose.runtime:runtime-saveable:1.6.7
                |               |         |         |         |    \--- androidx.compose.runtime:runtime-saveable-android:1.6.7
                |               |         |         |         |         +--- androidx.annotation:annotation:1.1.0 -> 1.7.0 (*)
                |               |         |         |         |         +--- androidx.compose.runtime:runtime:1.6.7 (*)
                |               |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 (*)
                |               |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22
                |               |         |         |         +--- androidx.compose.ui:ui-geometry:1.6.7
                |               |         |         |         |    \--- androidx.compose.ui:ui-geometry-android:1.6.7
                |               |         |         |         |         +--- androidx.annotation:annotation:1.1.0 -> 1.7.0 (*)
                |               |         |         |         |         +--- androidx.compose.runtime:runtime:1.2.1 -> 1.6.7 (*)
                |               |         |         |         |         +--- androidx.compose.ui:ui-util:1.6.7
                |               |         |         |         |         |    \--- androidx.compose.ui:ui-util-android:1.6.7
                |               |         |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 (*)
                |               |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22
                |               |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 (*)
                |               |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22
                |               |         |         |         +--- androidx.compose.ui:ui-graphics:1.6.7
                |               |         |         |         |    \--- androidx.compose.ui:ui-graphics-android:1.6.7
                |               |         |         |         |         +--- androidx.annotation:annotation:1.7.0 (*)
                |               |         |         |         |         +--- androidx.collection:collection:1.4.0 (*)
                |               |         |         |         |         +--- androidx.compose.runtime:runtime:1.6.7 (*)
                |               |         |         |         |         +--- androidx.compose.ui:ui-unit:1.6.7
                |               |         |         |         |         |    \--- androidx.compose.ui:ui-unit-android:1.6.7
                |               |         |         |         |         |         +--- androidx.annotation:annotation:1.1.0 -> 1.7.0 (*)
                |               |         |         |         |         |         +--- androidx.collection:collection-ktx:1.2.0 -> 1.4.0
                |               |         |         |         |         |         |    \--- androidx.collection:collection:1.4.0 (*)
                |               |         |         |         |         |         +--- androidx.compose.runtime:runtime:1.6.7 (*)
                |               |         |         |         |         |         +--- androidx.compose.ui:ui-geometry:1.6.7 (*)
                |               |         |         |         |         |         +--- androidx.compose.ui:ui-util:1.6.7 (*)
                |               |         |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 (*)
                |               |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22
                |               |         |         |         |         +--- androidx.compose.ui:ui-util:1.6.7 (*)
                |               |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22
                |               |         |         |         +--- androidx.compose.ui:ui-text:1.6.7
                |               |         |         |         |    \--- androidx.compose.ui:ui-text-android:1.6.7
                |               |         |         |         |         +--- androidx.annotation:annotation:1.1.0 -> 1.7.0 (*)
                |               |         |         |         |         +--- androidx.collection:collection:1.0.0 -> 1.4.0 (*)
                |               |         |         |         |         +--- androidx.compose.runtime:runtime:1.6.7 (*)
                |               |         |         |         |         +--- androidx.compose.runtime:runtime-saveable:1.6.7 (*)
                |               |         |         |         |         +--- androidx.compose.ui:ui-graphics:1.6.7 (*)
                |               |         |         |         |         +--- androidx.compose.ui:ui-unit:1.6.7 (*)
                |               |         |         |         |         +--- androidx.compose.ui:ui-util:1.6.7 (*)
                |               |         |         |         |         +--- androidx.core:core:1.7.0 -> 1.12.0 (*)
                |               |         |         |         |         +--- androidx.emoji2:emoji2:1.2.0 -> 1.3.0
                |               |         |         |         |         |    +--- androidx.annotation:annotation:1.2.0 -> 1.7.0 (*)
                |               |         |         |         |         |    +--- androidx.collection:collection:1.1.0 -> 1.4.0 (*)
                |               |         |         |         |         |    +--- androidx.core:core:1.3.0 -> 1.12.0 (*)
                |               |         |         |         |         |    +--- androidx.lifecycle:lifecycle-process:2.4.1 -> 2.6.1
                |               |         |         |         |         |    |    +--- androidx.annotation:annotation:1.2.0 -> 1.7.0 (*)
                |               |         |         |         |         |    |    +--- androidx.lifecycle:lifecycle-runtime:2.6.1 (*)
                |               |         |         |         |         |    |    +--- androidx.startup:startup-runtime:1.1.1 (*)
                |               |         |         |         |         |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 -> 1.8.22 (*)
                |               |         |         |         |         |    \--- androidx.startup:startup-runtime:1.0.0 -> 1.1.1 (*)
                |               |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 (*)
                |               |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22
                |               |         |         |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1 (*)
                |               |         |         |         +--- androidx.compose.ui:ui-unit:1.6.7 (*)
                |               |         |         |         +--- androidx.compose.ui:ui-util:1.6.7 (*)
                |               |         |         |         +--- androidx.core:core:1.12.0 (*)
                |               |         |         |         +--- androidx.customview:customview-poolingcontainer:1.0.0
                |               |         |         |         |    +--- androidx.core:core-ktx:1.5.0 -> 1.12.0 (*)
                |               |         |         |         |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.6.21 -> 1.8.22 (*)
                |               |         |         |         +--- androidx.emoji2:emoji2:1.2.0 -> 1.3.0 (*)
                |               |         |         |         +--- androidx.lifecycle:lifecycle-runtime:2.6.1 (*)
                |               |         |         |         +--- androidx.lifecycle:lifecycle-viewmodel:2.6.1 (*)
                |               |         |         |         +--- androidx.profileinstaller:profileinstaller:1.3.0 (*)
                |               |         |         |         +--- androidx.savedstate:savedstate-ktx:1.2.1 (*)
                |               |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 (*)
                |               |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22
                |               |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1 (*)
                |               |         |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1 (*)
                |               |         |         +--- androidx.compose.ui:ui-unit:1.6.7 (*)
                |               |         |         +--- androidx.compose.ui:ui-util:1.6.7 (*)
                |               |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 (*)
                |               |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22
                |               |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1 (*)
                |               |         +--- androidx.compose.foundation:foundation-layout:1.6.7
                |               |         |    \--- androidx.compose.foundation:foundation-layout-android:1.6.7
                |               |         |         +--- androidx.annotation:annotation:1.1.0 -> 1.7.0 (*)
                |               |         |         +--- androidx.compose.animation:animation-core:1.2.1 -> 1.6.7 (*)
                |               |         |         +--- androidx.compose.runtime:runtime:1.6.7 (*)
                |               |         |         +--- androidx.compose.ui:ui:1.6.7 (*)
                |               |         |         +--- androidx.compose.ui:ui-util:1.6.7 (*)
                |               |         |         +--- androidx.core:core:1.7.0 -> 1.12.0 (*)
                |               |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22
                |               |         +--- androidx.compose.runtime:runtime:1.6.7 (*)
                |               |         +--- androidx.compose.ui:ui:1.6.7 (*)
                |               |         +--- androidx.compose.ui:ui-geometry:1.6.7 (*)
                |               |         +--- androidx.compose.ui:ui-util:1.6.7 (*)
                |               |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22
                |               +--- androidx.compose.foundation:foundation-layout:1.6.7 (*)
                |               +--- androidx.compose.runtime:runtime:1.6.7 (*)
                |               +--- androidx.compose.ui:ui:1.6.7 (*)
                |               +--- androidx.compose.ui:ui-text:1.6.7 (*)
                |               +--- androidx.compose.ui:ui-util:1.6.7 (*)
                |               +--- androidx.core:core:1.12.0 (*)
                |               +--- androidx.emoji2:emoji2:1.3.0 (*)
                |               \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22
                """.trimMargin()
        )
    }

    @Test
    fun `androidx_activity activity-compose 1_7_2`(testInfo: TestInfo) {
        doTest(
            testInfo,
            platform = setOf(ResolutionPlatform.ANDROID),
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_MAVEN_GOOGLE),
            expected = """root
                |\--- androidx.activity:activity-compose:1.7.2
                |     +--- androidx.activity:activity-ktx:1.7.2
                |     |    +--- androidx.activity:activity:1.7.2
                |     |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     |    |    +--- androidx.core:core:1.8.0
                |     |    |    |    +--- androidx.annotation:annotation:1.2.0
                |     |    |    |    +--- androidx.annotation:annotation-experimental:1.1.0
                |     |    |    |    +--- androidx.lifecycle:lifecycle-runtime:2.3.1 -> 2.6.1
                |     |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     |    |    |    |    +--- androidx.arch.core:core-common:2.2.0
                |     |    |    |    |    |    \--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     |    |    |    |    +--- androidx.lifecycle:lifecycle-common:2.6.1
                |     |    |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10
                |     |    |    |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.10
                |     |    |    |    |    |    |    \--- org.jetbrains:annotations:13.0
                |     |    |    |    |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4
                |     |    |    |    |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4
                |     |    |    |    |    |         |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.4
                |     |    |    |    |    |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.4
                |     |    |    |    |    |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.21
                |     |    |    |    |    |         |         |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.6.21 -> 1.8.10 (*)
                |     |    |    |    |    |         |         |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.6.21
                |     |    |    |    |    |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.6.21 -> 1.8.10 (*)
                |     |    |    |    |    |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.6.21 -> 1.8.10
                |     |    |    |    |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.4
                |     |    |    |    |    |         \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.21 (*)
                |     |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     |    |    |    \--- androidx.versionedparcelable:versionedparcelable:1.1.1
                |     |    |    |         +--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     |    |    |         \--- androidx.collection:collection:1.0.0
                |     |    |    |              \--- androidx.annotation:annotation:1.0.0 -> 1.2.0
                |     |    |    +--- androidx.lifecycle:lifecycle-runtime:2.6.1 (*)
                |     |    |    +--- androidx.lifecycle:lifecycle-viewmodel:2.6.1
                |     |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     |    |    +--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.6.1
                |     |    |    |    +--- androidx.annotation:annotation:1.0.0 -> 1.2.0
                |     |    |    |    +--- androidx.core:core-ktx:1.2.0
                |     |    |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.3.41 -> 1.8.10 (*)
                |     |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     |    |    |    |    \--- androidx.core:core:1.2.0 -> 1.8.0 (*)
                |     |    |    |    +--- androidx.lifecycle:lifecycle-livedata-core:2.6.1
                |     |    |    |    |    +--- androidx.lifecycle:lifecycle-common:2.6.1 (*)
                |     |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     |    |    |    +--- androidx.lifecycle:lifecycle-viewmodel:2.6.1 (*)
                |     |    |    |    +--- androidx.savedstate:savedstate:1.2.1
                |     |    |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     |    |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     |    |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4 (*)
                |     |    |    +--- androidx.savedstate:savedstate:1.2.1 (*)
                |     |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     |    +--- androidx.core:core-ktx:1.1.0 -> 1.2.0 (*)
                |     |    +--- androidx.lifecycle:lifecycle-runtime-ktx:2.6.1
                |     |    |    +--- androidx.annotation:annotation:1.0.0 -> 1.2.0
                |     |    |    +--- androidx.lifecycle:lifecycle-runtime:2.6.1 (*)
                |     |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4 (*)
                |     |    +--- androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1
                |     |    |    +--- androidx.lifecycle:lifecycle-viewmodel:2.6.1 (*)
                |     |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4 (*)
                |     |    +--- androidx.savedstate:savedstate-ktx:1.2.1
                |     |    |    +--- androidx.savedstate:savedstate:1.2.1 (*)
                |     |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.10 (*)
                |     +--- androidx.compose.runtime:runtime:1.0.1
                |     |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.0 -> 1.6.4 (*)
                |     +--- androidx.compose.runtime:runtime-saveable:1.0.1
                |     |    +--- androidx.compose.runtime:runtime:1.0.1 (*)
                |     |    \--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     +--- androidx.compose.ui:ui:1.0.1
                |     |    +--- androidx.compose.runtime:runtime-saveable:1.0.1 (*)
                |     |    +--- androidx.compose.ui:ui-geometry:1.0.1
                |     |    |    \--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     |    +--- androidx.compose.ui:ui-graphics:1.0.1
                |     |    |    +--- androidx.annotation:annotation:1.2.0
                |     |    |    \--- androidx.compose.ui:ui-unit:1.0.1
                |     |    |         +--- androidx.compose.ui:ui-geometry:1.0.1 (*)
                |     |    |         \--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     |    +--- androidx.compose.ui:ui-text:1.0.1
                |     |    |    +--- androidx.compose.ui:ui-graphics:1.0.1 (*)
                |     |    |    +--- androidx.compose.ui:ui-unit:1.0.1 (*)
                |     |    |    \--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     |    +--- androidx.compose.ui:ui-unit:1.0.1 (*)
                |     |    \--- androidx.annotation:annotation:1.1.0 -> 1.2.0
                |     \--- androidx.lifecycle:lifecycle-viewmodel:2.6.1 (*)
            """.trimMargin()
        )
    }

    @Test
    fun `org_tinylog slf4j-tinylog 2_7_0-M1`(testInfo: TestInfo) {
        doTest(
            testInfo,
            expected = """root
                |\--- org.tinylog:slf4j-tinylog:2.7.0-M1
                |     +--- org.slf4j:slf4j-api:2.0.9
                |     \--- org.tinylog:tinylog-api:2.7.0-M1
            """.trimMargin()
        )
    }

    @Test
    fun `org_tinylog tinylog-api 2_7_0-M1`(testInfo: TestInfo) {
        doTest(
            testInfo,
            expected = """root
                |\--- org.tinylog:tinylog-api:2.7.0-M1
            """.trimMargin()
        )
    }

    @Test
    fun `androidx_appcompat appcompat 1_6_1`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_DL_GOOGLE_ANDROID),
            platform = setOf(ResolutionPlatform.ANDROID),
            expected = """root
                |\--- androidx.appcompat:appcompat:1.6.1
                |     +--- androidx.activity:activity:1.6.0
                |     |    +--- androidx.annotation:annotation:1.1.0 -> 1.3.0
                |     |    +--- androidx.core:core:1.8.0 -> 1.9.0
                |     |    |    +--- androidx.annotation:annotation:1.2.0 -> 1.3.0
                |     |    |    +--- androidx.annotation:annotation-experimental:1.3.0
                |     |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10
                |     |    |    |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.7.10
                |     |    |    |         \--- org.jetbrains:annotations:13.0
                |     |    |    +--- androidx.lifecycle:lifecycle-runtime:2.3.1 -> 2.5.1
                |     |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.3.0
                |     |    |    |    +--- androidx.arch.core:core-common:2.1.0
                |     |    |    |    |    \--- androidx.annotation:annotation:1.1.0 -> 1.3.0
                |     |    |    |    \--- androidx.lifecycle:lifecycle-common:2.5.1
                |     |    |    |         \--- androidx.annotation:annotation:1.1.0 -> 1.3.0
                |     |    |    \--- androidx.versionedparcelable:versionedparcelable:1.1.1
                |     |    |         +--- androidx.annotation:annotation:1.1.0 -> 1.3.0
                |     |    |         \--- androidx.collection:collection:1.0.0 -> 1.1.0
                |     |    |              \--- androidx.annotation:annotation:1.1.0 -> 1.3.0
                |     |    +--- androidx.lifecycle:lifecycle-runtime:2.5.1 (*)
                |     |    +--- androidx.lifecycle:lifecycle-viewmodel:2.5.1
                |     |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.3.0
                |     |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.6.21 -> 1.7.10 (*)
                |     |    +--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.5.1
                |     |    |    +--- androidx.annotation:annotation:1.0.0 -> 1.3.0
                |     |    |    +--- androidx.core:core-ktx:1.2.0
                |     |    |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.3.41 -> 1.7.10 (*)
                |     |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.3.0
                |     |    |    |    \--- androidx.core:core:1.2.0 -> 1.9.0 (*)
                |     |    |    +--- androidx.lifecycle:lifecycle-livedata-core:2.5.1
                |     |    |    |    \--- androidx.lifecycle:lifecycle-common:2.5.1 (*)
                |     |    |    +--- androidx.lifecycle:lifecycle-viewmodel:2.5.1 (*)
                |     |    |    +--- androidx.savedstate:savedstate:1.2.0
                |     |    |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.3.0
                |     |    |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.6.20 -> 1.7.10 (*)
                |     |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.6.21 -> 1.7.10 (*)
                |     |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.1
                |     |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.1
                |     |    |         |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.1
                |     |    |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.1
                |     |    |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.0
                |     |    |         |         |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.6.0 -> 1.7.10 (*)
                |     |    |         |         |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.6.0
                |     |    |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.6.0 -> 1.7.10 (*)
                |     |    |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.6.0 -> 1.7.10
                |     |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.1
                |     |    |         \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.0 (*)
                |     |    +--- androidx.savedstate:savedstate:1.2.0 (*)
                |     |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 (*)
                |     +--- androidx.annotation:annotation:1.3.0
                |     +--- androidx.appcompat:appcompat-resources:1.6.1
                |     |    +--- androidx.annotation:annotation:1.2.0 -> 1.3.0
                |     |    +--- androidx.core:core:1.6.0 -> 1.9.0 (*)
                |     |    +--- androidx.vectordrawable:vectordrawable:1.1.0
                |     |    |    +--- androidx.annotation:annotation:1.1.0 -> 1.3.0
                |     |    |    +--- androidx.core:core:1.1.0 -> 1.9.0 (*)
                |     |    |    \--- androidx.collection:collection:1.1.0 (*)
                |     |    \--- androidx.vectordrawable:vectordrawable-animated:1.1.0
                |     |         +--- androidx.vectordrawable:vectordrawable:1.1.0 (*)
                |     |         +--- androidx.interpolator:interpolator:1.0.0
                |     |         |    \--- androidx.annotation:annotation:1.0.0 -> 1.3.0
                |     |         \--- androidx.collection:collection:1.1.0 (*)
                |     +--- androidx.core:core:1.9.0 (*)
                |     +--- androidx.cursoradapter:cursoradapter:1.0.0
                |     |    \--- androidx.annotation:annotation:1.0.0 -> 1.3.0
                |     +--- androidx.drawerlayout:drawerlayout:1.0.0
                |     |    +--- androidx.annotation:annotation:1.0.0 -> 1.3.0
                |     |    +--- androidx.core:core:1.0.0 -> 1.9.0 (*)
                |     |    \--- androidx.customview:customview:1.0.0
                |     |         +--- androidx.annotation:annotation:1.0.0 -> 1.3.0
                |     |         \--- androidx.core:core:1.0.0 -> 1.9.0 (*)
                |     +--- androidx.fragment:fragment:1.3.6
                |     |    +--- androidx.annotation:annotation:1.1.0 -> 1.3.0
                |     |    +--- androidx.core:core:1.2.0 -> 1.9.0 (*)
                |     |    +--- androidx.collection:collection:1.1.0 (*)
                |     |    +--- androidx.viewpager:viewpager:1.0.0
                |     |    |    +--- androidx.annotation:annotation:1.0.0 -> 1.3.0
                |     |    |    +--- androidx.core:core:1.0.0 -> 1.9.0 (*)
                |     |    |    \--- androidx.customview:customview:1.0.0 (*)
                |     |    +--- androidx.loader:loader:1.0.0
                |     |    |    +--- androidx.annotation:annotation:1.0.0 -> 1.3.0
                |     |    |    +--- androidx.core:core:1.0.0 -> 1.9.0 (*)
                |     |    |    +--- androidx.lifecycle:lifecycle-livedata:2.0.0
                |     |    |    |    +--- androidx.arch.core:core-runtime:2.0.0
                |     |    |    |    |    +--- androidx.annotation:annotation:1.0.0 -> 1.3.0
                |     |    |    |    |    \--- androidx.arch.core:core-common:2.0.0 -> 2.1.0 (*)
                |     |    |    |    +--- androidx.lifecycle:lifecycle-livedata-core:2.0.0 -> 2.5.1 (*)
                |     |    |    |    \--- androidx.arch.core:core-common:2.0.0 -> 2.1.0 (*)
                |     |    |    \--- androidx.lifecycle:lifecycle-viewmodel:2.0.0 -> 2.5.1 (*)
                |     |    +--- androidx.activity:activity:1.2.4 -> 1.6.0 (*)
                |     |    +--- androidx.lifecycle:lifecycle-livedata-core:2.3.1 -> 2.5.1 (*)
                |     |    +--- androidx.lifecycle:lifecycle-viewmodel:2.3.1 -> 2.5.1 (*)
                |     |    +--- androidx.lifecycle:lifecycle-viewmodel-savedstate:2.3.1 -> 2.5.1 (*)
                |     |    +--- androidx.savedstate:savedstate:1.1.0 -> 1.2.0 (*)
                |     |    \--- androidx.annotation:annotation-experimental:1.0.0 -> 1.3.0 (*)
                |     \--- androidx.savedstate:savedstate:1.2.0 (*)
            """.trimMargin()
        )
        val appcompat = root.children.single() as MavenDependencyNode
        assertEquals("androidx.appcompat:appcompat:1.6.1", appcompat.toString())
        assertEquals(listOf("aar"), appcompat.dependency.files().map { it.extension }.sortedBy { it })
        assertFiles("""
            activity-1.6.0.aar
            annotation-1.3.0.jar
            annotation-experimental-1.3.0.aar
            annotations-13.0.jar
            appcompat-1.6.1.aar
            appcompat-resources-1.6.1.aar
            collection-1.1.0.jar
            core-1.9.0.aar
            core-common-2.1.0.jar
            core-ktx-1.2.0.aar
            core-runtime-2.0.0.aar
            cursoradapter-1.0.0.aar
            customview-1.0.0.aar
            drawerlayout-1.0.0.aar
            fragment-1.3.6.aar
            interpolator-1.0.0.aar
            kotlin-stdlib-1.7.10.jar
            kotlin-stdlib-common-1.7.10.jar
            kotlin-stdlib-jdk7-1.6.0.jar
            kotlin-stdlib-jdk8-1.6.0.jar
            kotlinx-coroutines-android-1.6.1.jar
            kotlinx-coroutines-core-jvm-1.6.1.jar
            lifecycle-common-2.5.1.jar
            lifecycle-livedata-2.0.0.aar
            lifecycle-livedata-core-2.5.1.aar
            lifecycle-runtime-2.5.1.aar
            lifecycle-viewmodel-2.5.1.aar
            lifecycle-viewmodel-savedstate-2.5.1.aar
            loader-1.0.0.aar
            savedstate-1.2.0.aar
            vectordrawable-1.1.0.aar
            vectordrawable-animated-1.1.0.aar
            versionedparcelable-1.1.1.aar
            viewpager-1.0.0.aar
        """.trimIndent(), root
        )
    }

    @Test
    fun `androidx_appcompat appcompat 1_6_1 many contexts`() {
        val repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_DL_GOOGLE_ANDROID).toRepositories()
        val contexts = listOf(
            context(platform = setOf(ResolutionPlatform.JVM), repositories = repositories),
            context(platform = setOf(ResolutionPlatform.ANDROID), repositories = repositories),
        )

        val root = DependencyNodeHolder(
            "root",
            contexts.map { "androidx.appcompat:appcompat:1.6.1".toMavenNode(it) },
            context()
        )

        runBlocking {
            doTest(root, verifyMessages = true)
        }
    }

    @Test
    fun `com_google_guava guava 33_0_0-android`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            platform = setOf(ResolutionPlatform.ANDROID),
            expected = """root
                |\--- com.google.guava:guava:33.0.0-android
                |     +--- com.google.guava:failureaccess:1.0.2
                |     +--- com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava
                |     +--- com.google.code.findbugs:jsr305:3.0.2
                |     +--- org.checkerframework:checker-qual:3.41.0
                |     +--- com.google.errorprone:error_prone_annotations:2.23.0
                |     \--- com.google.j2objc:j2objc-annotations:2.8
            """.trimMargin()
        )
        assertFiles(
            """checker-qual-3.41.0-javadoc.jar
                |checker-qual-3.41.0-sources.jar
                |checker-qual-3.41.0.jar
                |error_prone_annotations-2.23.0-sources.jar
                |error_prone_annotations-2.23.0.jar
                |failureaccess-1.0.2-sources.jar
                |failureaccess-1.0.2.jar
                |guava-33.0.0-android-sources.jar
                |guava-33.0.0-android.jar
                |j2objc-annotations-2.8-sources.jar
                |j2objc-annotations-2.8.jar
                |jsr305-3.0.2-sources.jar
                |jsr305-3.0.2.jar
                |listenablefuture-9999.0-empty-to-avoid-conflict-with-guava-sources.jar
                |listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar
            """.trimMargin(),
            root,
            true
        )
    }

    /**
     * This test checks that guava dependency is added to the graph in spite of it defines several
     * capabilities, while Amper doesn't allow such libraries in the graph
     * (apart from several exceptional cases, including a guava library).
     *
     * Allowing libraries with multiple capabilities in a graph would lead to a potential runtime conflict
     * (when libraries with the same capabilities are added to the runtime).
     * Resolution of conflict between libraries with the same capability should be explicitly supported in Amper DR.
     * Until that, such libraries are denied.
     */
    @Test
    fun `com_google_guava guava 32_1_1-jre`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            expected = """root
                |\--- com.google.guava:guava:32.1.1-jre
                |     +--- com.google.guava:guava-parent:32.1.1-jre
                |     +--- com.google.guava:failureaccess:1.0.1
                |     +--- com.google.code.findbugs:jsr305:3.0.2
                |     +--- org.checkerframework:checker-qual:3.33.0
                |     +--- com.google.errorprone:error_prone_annotations:2.18.0
                |     \--- com.google.j2objc:j2objc-annotations:2.8
            """.trimMargin()
        )
        runBlocking {
            downloadAndAssertFiles(
                """checker-qual-3.33.0-javadoc.jar
                |checker-qual-3.33.0-sources.jar
                |checker-qual-3.33.0.jar
                |error_prone_annotations-2.18.0-sources.jar
                |error_prone_annotations-2.18.0.jar
                |failureaccess-1.0.1-sources.jar
                |failureaccess-1.0.1.jar
                |guava-32.1.1-jre-sources.jar
                |guava-32.1.1-jre.jar
                |j2objc-annotations-2.8-sources.jar
                |j2objc-annotations-2.8.jar
                |jsr305-3.0.2-sources.jar
                |jsr305-3.0.2.jar
            """.trimMargin(),
                root, true, verifyMessages = true
            )
        }
    }

    @Test
    fun `org_jetbrains_packagesearch packagesearch-plugin 1_0_0-SNAPSHOT`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_JETBRAINS_KPM_PUBLIC),
            expected = """root
                |\--- org.jetbrains.packagesearch:packagesearch-plugin:1.0.0-SNAPSHOT
                |     \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.0
                |          +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.0
                |          |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.0
                |          |    \--- org.jetbrains:annotations:13.0
                |          \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.0
                |               \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.0 (*)
            """.trimMargin()
        )

        runBlocking {
            downloadAndAssertFiles(
                """annotations-13.0.jar
                |kotlin-stdlib-1.9.0.jar
                |kotlin-stdlib-common-1.9.0.jar
                |kotlin-stdlib-jdk7-1.9.0.jar
                |kotlin-stdlib-jdk8-1.9.0.jar
                |packagesearch-plugin-1.0.0-SNAPSHOT.jar"""
                    .trimMargin(),
                root,
            )
        }
    }

    /**
     * This test checks that a library published with the version '*-SNAPSHOT' but without maven-metadata.xml is
     * downloaded as a usual library (fallback to release flow).
     */
    @Test
    fun `com_jetbrains_intellij_platform core-impl 251_23774_109-EAP-SNAPSHOT`(testInfo: TestInfo) {
        doTest(
            testInfo,
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_INTELLIJ_DEPS, REDIRECTOR_INTELLIJ_SNAPSHOTS),
            expected = """root
                |\--- com.jetbrains.intellij.platform:core-impl:251.23774.109-EAP-SNAPSHOT
                |     +--- com.jetbrains.intellij.platform:core:251.23774.109-EAP-SNAPSHOT
                |     |    \--- com.jetbrains.intellij.platform:extensions:251.23774.109-EAP-SNAPSHOT
                |     |         \--- com.intellij.platform:kotlinx-coroutines-core-jvm:1.8.0-intellij-12
                |     |              +--- org.jetbrains:annotations:23.0.0
                |     |              +--- com.intellij.platform:kotlinx-coroutines-bom:1.8.0-intellij-12
                |     |              \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 2.1.10
                |     |                   \--- org.jetbrains:annotations:13.0 -> 23.0.0
                |     \--- org.jetbrains.kotlin:kotlin-stdlib:2.1.10 (*)
            """.trimMargin()
        )
    }

    /**
     * BOM is published with pom.xml and with Gradle metadata (.module file).
     * Such a BOM being imported applies dependency constraints to the resolution graph
     */
    @Test
    fun `io_ktor ktor-bom 2_3_9`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            dependency = "bom:io.ktor:ktor-bom:2.3.9",
            scope = ResolutionScope.RUNTIME,
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_JETBRAINS_KPM_PUBLIC),
            expected = """root
                |\--- io.ktor:ktor-bom:2.3.9
            """.trimMargin()
        )

        val constraintsNumber = root
            .distinctBfsSequence()
            .filterIsInstance<MavenDependencyConstraintNode>()
            .count()

        assertEquals(386, constraintsNumber,
            "Unexpected list of constraints, it should contain 386 items, but contains $constraintsNumber")
    }

    /**
     * Dependency on a BOM as on a regular dependency is prohibited if BOM is published with Gradle metadata
     */
    @Test
    fun `declaring BOM published with Gradle metadata as a regular dependency`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            dependency = "io.ktor:ktor-bom:2.3.9",
            scope = ResolutionScope.RUNTIME,
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_JETBRAINS_KPM_PUBLIC),
            expected = """root
                |\--- io.ktor:ktor-bom:2.3.9
            """.trimMargin(),
            verifyMessages = false
        )

        val messages = root.children.single().messages.defaultFilterMessages()
        assertNotNull(messages.singleOrNull(), "The only error message is expected, but found: ${messages.toSet()}")
        assertEquals(
            "No variant for the platform jvm is provided by the library io.ktor:ktor-bom:2.3.9",
            messages.singleOrNull()!!.message,
            "Unexpected error message"
        )

        val constraintsNumber = root
            .distinctBfsSequence()
            .filterIsInstance<MavenDependencyConstraintNode>()
            .count()

        assertEquals(0, constraintsNumber,
            "Unexpected list of constraints, it should contain 0 items, but contains $constraintsNumber")
    }

    /**
     * BOM is published with pom.xml only (no Gradle metadata in .module file)
     */
    @Test
    fun `com_fasterxml_jackson jackson-bom 2_18_3`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            dependency = "bom:com.fasterxml.jackson:jackson-bom:2.18.3",
            scope = ResolutionScope.RUNTIME,
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL),
            expected = """root
                |\--- com.fasterxml.jackson:jackson-bom:2.18.3
            """.trimMargin()
        )

        val constraintsNumber = root
            .distinctBfsSequence()
            .filterIsInstance<MavenDependencyConstraintNode>()
            .count()

        assertEquals(66, constraintsNumber, "Unexpected list of constraints, it should contain 66 items, but contains $constraintsNumber")
    }

    /**
     * Direct dependency with an unspecified version is properly reported.
     */
    @Test
    fun `error if direct dependency version is unspecified`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            dependency = "com.fasterxml.jackson.core:jackson-annotations",
            scope = ResolutionScope.RUNTIME,
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL),
            expected = """root
               |\--- com.fasterxml.jackson.core:jackson-annotations:unspecified
            """.trimMargin(),
            verifyMessages = false
        )

        val messages = root.children.single().messages.defaultFilterMessages()
        assertNotNull(messages.singleOrNull(), "The only error message is expected, but found: ${messages.toSet()}")
        assertEquals(
            "Version of dependency is not specified, it has not been resolved by dependency resolution",
            messages.singleOrNull()!!.message,
            "Unexpected error message"
        )
    }

    /**
     * Dependency on a BOM as on a regular dependency is NOOP if BOM is published as a pom.xml only (without Gradle metadata)
     */
    @Test
    fun `declaring BOM published without Gradle metadata as a regular dependency`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            dependency = "com.fasterxml.jackson:jackson-bom:2.18.3",
            scope = ResolutionScope.RUNTIME,
            expected = """root
                |\--- com.fasterxml.jackson:jackson-bom:2.18.3
            """.trimMargin()
        )

        assertFiles("", root)

        val constraintsNumber = root
            .distinctBfsSequence()
            .filterIsInstance<MavenDependencyConstraintNode>()
            .count()

        assertEquals(0, constraintsNumber,
            "Unexpected list of constraints, it should contain 0 items, but contains $constraintsNumber")
    }

    @Test
    fun `com_fasterxml_jackson_core jackson-annotations 2_18_3`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            dependency = listOf(
                "com.fasterxml.jackson.core:jackson-annotations:2.18.3",
                "com.fasterxml.jackson.core:jackson-databind:2.18.2"),
            expected = """root
               |+--- com.fasterxml.jackson.core:jackson-annotations:2.18.3
               ||    \--- com.fasterxml.jackson:jackson-bom:2.18.3
               ||         \--- com.fasterxml.jackson.core:jackson-databind:2.18.3 (c)
               |\--- com.fasterxml.jackson.core:jackson-databind:2.18.2 -> 2.18.3
               |     +--- com.fasterxml.jackson.core:jackson-annotations:2.18.3 (*)
               |     +--- com.fasterxml.jackson.core:jackson-core:2.18.3
               |     |    \--- com.fasterxml.jackson:jackson-bom:2.18.3
               |     \--- com.fasterxml.jackson:jackson-bom:2.18.3
            """.trimMargin()
        )
        runBlocking {
            downloadAndAssertFiles(
                """jackson-annotations-2.18.3.jar
                    |jackson-core-2.18.3.jar
                    |jackson-databind-2.18.3.jar
              """.trimMargin(),
                root, verifyMessages = true
            )
        }
    }

    /**
     * org.jetbrains.amper:amper-dr-test-bom-usages:1.0 is published from the test project
     * testData/amper-dr-test-bom-usages
     */
    @Test
    fun `org_jetbrains_amper amper-dr-test-bom-usages 1_0`(testInfo: TestInfo) {
        doTest(
            testInfo,
            scope = ResolutionScope.RUNTIME,
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, "https://packages.jetbrains.team/maven/p/amper/amper"),
            expected = """root
               |\--- org.jetbrains.amper:amper-dr-test-bom-usages:1.0
               |     +--- io.ktor:ktor-bom:2.3.9
               |     |    \--- io.ktor:ktor-client-core-jvm:2.3.9 (c)
               |     +--- io.ktor:ktor-io-jvm:2.3.9
               |     |    +--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22
               |     |    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.20
               |     |    |         +--- org.jetbrains:annotations:13.0 -> 23.0.0
               |     |    |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:2.0.20 (c)
               |     |    +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22
               |     |    |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 2.0.20 (*)
               |     |    |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22 (*)
               |     |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.1
               |     |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1
               |     |    |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.1
               |     |    |    |         +--- org.jetbrains:annotations:23.0.0
               |     |    |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.7.1
               |     |    |    |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.20 -> 2.0.20
               |     |    |    |         |    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.20 (*)
               |     |    |    |         \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.20 -> 1.8.22 (*)
               |     |    |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.7.1
               |     |    |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.20 -> 1.8.22 (*)
               |     |    +--- org.slf4j:slf4j-api:1.7.36
               |     |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1 (*)
               |     |    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.20 (*)
               |     +--- io.ktor:ktor-client-core-jvm:2.3.8 -> 2.3.9
               |     |    +--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22 (*)
               |     |    +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22 (*)
               |     |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.1 (*)
               |     |    +--- org.slf4j:slf4j-api:1.7.36
               |     |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1 (*)
               |     |    +--- io.ktor:ktor-http:2.3.9
               |     |    |    \--- io.ktor:ktor-http-jvm:2.3.9
               |     |    |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22 (*)
               |     |    |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22 (*)
               |     |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.1 (*)
               |     |    |         +--- org.slf4j:slf4j-api:1.7.36
               |     |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1 (*)
               |     |    |         +--- io.ktor:ktor-utils:2.3.9
               |     |    |         |    \--- io.ktor:ktor-utils-jvm:2.3.9
               |     |    |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22 (*)
               |     |    |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22 (*)
               |     |    |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.1 (*)
               |     |    |         |         +--- org.slf4j:slf4j-api:1.7.36
               |     |    |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1 (*)
               |     |    |         |         +--- io.ktor:ktor-io:2.3.9
               |     |    |         |         |    \--- io.ktor:ktor-io-jvm:2.3.9 (*)
               |     |    |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.20 (*)
               |     |    |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.20 (*)
               |     |    +--- io.ktor:ktor-events:2.3.9
               |     |    |    \--- io.ktor:ktor-events-jvm:2.3.9
               |     |    |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22 (*)
               |     |    |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22 (*)
               |     |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.1 (*)
               |     |    |         +--- org.slf4j:slf4j-api:1.7.36
               |     |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1 (*)
               |     |    |         +--- io.ktor:ktor-http:2.3.9 (*)
               |     |    |         +--- io.ktor:ktor-utils:2.3.9 (*)
               |     |    |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.20 (*)
               |     |    +--- io.ktor:ktor-websocket-serialization:2.3.9
               |     |    |    \--- io.ktor:ktor-websocket-serialization-jvm:2.3.9
               |     |    |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22 (*)
               |     |    |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22 (*)
               |     |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.1 (*)
               |     |    |         +--- org.slf4j:slf4j-api:1.7.36
               |     |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1 (*)
               |     |    |         +--- io.ktor:ktor-http:2.3.9 (*)
               |     |    |         +--- io.ktor:ktor-serialization:2.3.9
               |     |    |         |    \--- io.ktor:ktor-serialization-jvm:2.3.9
               |     |    |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22 (*)
               |     |    |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22 (*)
               |     |    |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.1 (*)
               |     |    |         |         +--- org.slf4j:slf4j-api:1.7.36
               |     |    |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1 (*)
               |     |    |         |         +--- io.ktor:ktor-http:2.3.9 (*)
               |     |    |         |         +--- io.ktor:ktor-websockets:2.3.9
               |     |    |         |         |    \--- io.ktor:ktor-websockets-jvm:2.3.9
               |     |    |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22 (*)
               |     |    |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22 (*)
               |     |    |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.1 (*)
               |     |    |         |         |         +--- org.slf4j:slf4j-api:1.7.36
               |     |    |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1 (*)
               |     |    |         |         |         +--- io.ktor:ktor-http:2.3.9 (*)
               |     |    |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.20 (*)
               |     |    |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.20 (*)
               |     |    |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.20 (*)
               |     |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.22 -> 2.0.20 (*)
               |     |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.7.1
               |     |         +--- org.slf4j:slf4j-api:1.7.32 -> 1.7.36
               |     |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1 (*)
               |     |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.7.1
               |     |         \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.20 -> 1.8.22 (*)
               |     \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.20 (*)
            """.trimMargin()
        )
    }

    @Test
    fun `junit junit 4_10`(testInfo: TestInfo) {
        doTest(
            testInfo,
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_MAVEN_GOOGLE),
            expected = """root
                |\--- junit:junit:4.10
                |     \--- org.hamcrest:hamcrest-core:1.1
            """.trimMargin()
        )
    }

    @Test
    fun `io_ktor ktor-server-auth 2_2_2`(testInfo: TestInfo) {
        doTest(
            testInfo,
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_MAVEN_GOOGLE),
            expected = """root
                |\--- io.ktor:ktor-server-auth:2.2.2
                |     \--- io.ktor:ktor-server-auth-jvm:2.2.2
                |          +--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.7.20
                |          |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.20 -> 1.7.22
                |          |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.7.22
                |          |         \--- org.jetbrains:annotations:13.0
                |          +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.20
                |          |    +--- org.jetbrains.kotlin:kotlin-stdlib:1.7.20 -> 1.7.22 (*)
                |          |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.7.20 (*)
                |          +--- org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4
                |          |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4
                |          |    |    \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.4
                |          |    |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.4
                |          |    |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.21 -> 1.7.20 (*)
                |          |    |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.6.21 -> 1.7.22
                |          |    +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.4
                |          |    \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.21 -> 1.7.20 (*)
                |          +--- org.slf4j:slf4j-api:1.7.36
                |          +--- com.googlecode.json-simple:json-simple:1.1.1
                |          |    \--- junit:junit:4.10
                |          |         \--- org.hamcrest:hamcrest-core:1.1
                |          +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4 (*)
                |          +--- io.ktor:ktor-server-core:2.2.2
                |          |    \--- io.ktor:ktor-server-core-jvm:2.2.2
                |          |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.7.20 (*)
                |          |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.20 (*)
                |          |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4 (*)
                |          |         +--- org.slf4j:slf4j-api:1.7.36
                |          |         +--- com.typesafe:config:1.4.2
                |          |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4 (*)
                |          |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.7.20 -> 1.7.22
                |          |         +--- io.ktor:ktor-utils:2.2.2
                |          |         |    \--- io.ktor:ktor-utils-jvm:2.2.2
                |          |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.7.20 (*)
                |          |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.20 (*)
                |          |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4 (*)
                |          |         |         +--- org.slf4j:slf4j-api:1.7.36
                |          |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4 (*)
                |          |         |         +--- io.ktor:ktor-io:2.2.2
                |          |         |         |    \--- io.ktor:ktor-io-jvm:2.2.2
                |          |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.7.20 (*)
                |          |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.20 (*)
                |          |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4 (*)
                |          |         |         |         +--- org.slf4j:slf4j-api:1.7.36
                |          |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4 (*)
                |          |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.7.20 -> 1.7.22
                |          |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.7.20 -> 1.7.22
                |          |         +--- io.ktor:ktor-http:2.2.2
                |          |         |    \--- io.ktor:ktor-http-jvm:2.2.2
                |          |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.7.20 (*)
                |          |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.20 (*)
                |          |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4 (*)
                |          |         |         +--- org.slf4j:slf4j-api:1.7.36
                |          |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4 (*)
                |          |         |         +--- io.ktor:ktor-utils:2.2.2 (*)
                |          |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.7.20 -> 1.7.22
                |          |         +--- io.ktor:ktor-serialization:2.2.2
                |          |         |    \--- io.ktor:ktor-serialization-jvm:2.2.2
                |          |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.7.20 (*)
                |          |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.20 (*)
                |          |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4 (*)
                |          |         |         +--- org.slf4j:slf4j-api:1.7.36
                |          |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4 (*)
                |          |         |         +--- io.ktor:ktor-http:2.2.2 (*)
                |          |         |         +--- io.ktor:ktor-websockets:2.2.2
                |          |         |         |    \--- io.ktor:ktor-websockets-jvm:2.2.2
                |          |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.7.20 (*)
                |          |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.20 (*)
                |          |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4 (*)
                |          |         |         |         +--- org.slf4j:slf4j-api:1.7.36
                |          |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4 (*)
                |          |         |         |         +--- io.ktor:ktor-http:2.2.2 (*)
                |          |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.7.20 -> 1.7.22
                |          |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.7.20 -> 1.7.22
                |          |         +--- io.ktor:ktor-events:2.2.2
                |          |         |    \--- io.ktor:ktor-events-jvm:2.2.2
                |          |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.7.20 (*)
                |          |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.20 (*)
                |          |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4 (*)
                |          |         |         +--- org.slf4j:slf4j-api:1.7.36
                |          |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4 (*)
                |          |         |         +--- io.ktor:ktor-http:2.2.2 (*)
                |          |         |         +--- io.ktor:ktor-utils:2.2.2 (*)
                |          |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.7.20 -> 1.7.22
                |          |         \--- org.jetbrains.kotlin:kotlin-reflect:1.7.22
                |          |              \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.22 (*)
                |          +--- io.ktor:ktor-client-core:2.2.2
                |          |    \--- io.ktor:ktor-client-core-jvm:2.2.2
                |          |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.7.20 (*)
                |          |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.20 (*)
                |          |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4 (*)
                |          |         +--- org.slf4j:slf4j-api:1.7.36
                |          |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4 (*)
                |          |         +--- io.ktor:ktor-http:2.2.2 (*)
                |          |         +--- io.ktor:ktor-events:2.2.2 (*)
                |          |         +--- io.ktor:ktor-websocket-serialization:2.2.2
                |          |         |    \--- io.ktor:ktor-websocket-serialization-jvm:2.2.2
                |          |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.7.20 (*)
                |          |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.20 (*)
                |          |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4 (*)
                |          |         |         +--- org.slf4j:slf4j-api:1.7.36
                |          |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4 (*)
                |          |         |         +--- io.ktor:ktor-http:2.2.2 (*)
                |          |         |         +--- io.ktor:ktor-serialization:2.2.2 (*)
                |          |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.7.20 -> 1.7.22
                |          |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.7.20 -> 1.7.22
                |          +--- io.ktor:ktor-server-sessions:2.2.2
                |          |    \--- io.ktor:ktor-server-sessions-jvm:2.2.2
                |          |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.7.20 (*)
                |          |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.20 (*)
                |          |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4 (*)
                |          |         +--- org.slf4j:slf4j-api:1.7.36
                |          |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4 (*)
                |          |         +--- io.ktor:ktor-server-core:2.2.2 (*)
                |          |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.7.20 -> 1.7.22
                |          |         +--- org.jetbrains.kotlinx:kotlinx-serialization-core:1.4.1
                |          |         |    \--- org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.4.1
                |          |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.7.20 -> 1.7.22 (*)
                |          |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.7.20 -> 1.7.22
                |          |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.20 (*)
                |          |         \--- org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1
                |          |              \--- org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.4.1
                |          |                   +--- org.jetbrains.kotlin:kotlin-stdlib:1.7.20 -> 1.7.22 (*)
                |          |                   +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.7.20 -> 1.7.22
                |          |                   +--- org.jetbrains.kotlinx:kotlinx-serialization-core:1.4.1 (*)
                |          |                   \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.20 (*)
                |          \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.7.20 -> 1.7.22
            """.trimMargin()
        )
    }

    @Test
    fun `org_jetbrains_kotlinx kotlinx-datetime 0_5_0`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            platform = setOf(ResolutionPlatform.MACOS_ARM64),
            expected = """root
                |\--- org.jetbrains.kotlinx:kotlinx-datetime:0.5.0
                |     \--- org.jetbrains.kotlinx:kotlinx-datetime-macosarm64:0.5.0
                |          +--- org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2
                |          |    \--- org.jetbrains.kotlinx:kotlinx-serialization-core-macosarm64:1.6.2
                |          |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.21
                |          |         |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21
                |          |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21
                |          \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21
            """.trimMargin()
        )
        assertFiles(
            """kotlinx-datetime-macosarm64-0.5.0.klib
                |kotlinx-serialization-core-macosarm64-1.6.2.klib""".trimMargin(),
            root
        )
    }

    @Test
    fun `org_jetbrains_kotlinx kotlinx-serialization-json 1_7_2`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            platform = setOf(ResolutionPlatform.MACOS_ARM64),
            expected = """root
                |\--- org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2
                |     \--- org.jetbrains.kotlinx:kotlinx-serialization-json-macosarm64:1.7.2
                |          +--- org.jetbrains.kotlin:kotlin-stdlib-common:2.0.20
                |          |    \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.20
                |          +--- org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.2
                |          |    \--- org.jetbrains.kotlinx:kotlinx-serialization-core-macosarm64:1.7.2
                |          |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:2.0.20 (*)
                |          |         \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.20
                |          \--- org.jetbrains.kotlin:kotlin-stdlib:2.0.20
            """.trimMargin()
        )
        assertFiles(
            """kotlinx-serialization-core-macosarm64-1.7.2.klib
                |kotlinx-serialization-json-macosarm64-1.7.2.klib""".trimMargin(),
            root
        )
    }

    /**
     * TODO: org.jetbrains.kotlin:kotlin-test-junit:1.9.20 (*) is missing from org.jetbrains.kotlin:kotlin-test:1.9.20
     */
    @Test
    fun `kotlin test with junit`() {
        context().use { context ->
            val rootNode = listOf(
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.20",
                "org.jetbrains.kotlin:kotlin-test-junit:1.9.20",
                "org.jetbrains.kotlin:kotlin-test:1.9.20",
                "junit:junit:4.12",
            ).toRootNode(context)
            val resolver = Resolver()
            val root = rootNode
            runBlocking { resolver.buildGraph(root, ResolutionLevel.NETWORK) }
            assertEquals(
                """root
               |+--- org.jetbrains.kotlin:kotlin-stdlib:1.9.20
               ||    \--- org.jetbrains:annotations:13.0
               |+--- org.jetbrains.kotlin:kotlin-test-junit:1.9.20
               ||    +--- org.jetbrains.kotlin:kotlin-test:1.9.20
               ||    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.20 (*)
               ||    \--- junit:junit:4.13.2
               ||         \--- org.hamcrest:hamcrest-core:1.3
               |+--- org.jetbrains.kotlin:kotlin-test:1.9.20 (*)
               |\--- junit:junit:4.12 -> 4.13.2 (*)
        """.trimMargin(),
                root
            )
        }
    }

    /**
     * The test checks that dependencies defined with test scope in pom.xml are ignored by dependency resolution.
     * Such dependencies are not transitive and are used for compiling and running tests of dependency itself.
     *
     * Library org.apache.logging.log4j:log4j-api:2.17.1 defines test dependencies on:
     *  - org.junit.vintage:junit-vintage-engine:5.7.2
     *  - org.junit.jupiter:junit-jupiter-migrationsupport:5.7.2
     *  - org.junit.jupiter:junit-jupiter-params:5.7.2
     *  - org.junit.jupiter:junit-jupiter-engine:5.7.2
     *  - org.assertj:assertj-core:3.20.2
     *
     *  DR should ignore all of those dependencies as those belong to the test scope.
     */
    @Test
    fun `org_apache_logging_log4j log4j-core 2_17_1`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            scope = ResolutionScope.RUNTIME,
            verifyMessages = true,
            expected = """root
                |\--- org.apache.logging.log4j:log4j-core:2.17.1
                |     \--- org.apache.logging.log4j:log4j-api:2.17.1
            """.trimMargin()
        )

        runBlocking {
            downloadAndAssertFiles(
                """log4j-api-2.17.1.jar
                |log4j-core-2.17.1.jar""".trimMargin(),
                root)
        }
    }

    /**
     * Check that pom.xml with an empty dependencyManagement section is parsed successfully
     */
    @Test
    fun `org_openjfx javafx 24-ea+5`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            expected = """root
                |\--- org.openjfx:javafx:24-ea+5"""
                .trimMargin()
        )

        runBlocking {
            downloadAndAssertFiles("", root)
        }
    }

    @Test
    fun `org_junit_jupiter junit-jupiter-params 5_7_2`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            scope = ResolutionScope.RUNTIME,
            verifyMessages = false, // todo (AB) : It should be replaced, resolution warning should be fixed
            expected = """root
                |\--- org.junit.jupiter:junit-jupiter-params:5.7.2
                |     +--- org.junit:junit-bom:5.7.2
                |     +--- org.apiguardian:apiguardian-api:1.1.0
                |     \--- org.junit.jupiter:junit-jupiter-api:5.7.2
                |          +--- org.junit:junit-bom:5.7.2
                |          +--- org.apiguardian:apiguardian-api:1.1.0
                |          +--- org.opentest4j:opentest4j:1.2.0
                |          \--- org.junit.platform:junit-platform-commons:1.7.2
                |               +--- org.junit:junit-bom:5.7.2
                |               \--- org.apiguardian:apiguardian-api:1.1.0
            """.trimMargin()
        )

        runBlocking {
            downloadAndAssertFiles(
                """apiguardian-api-1.1.0.jar
                |junit-jupiter-api-5.7.2.jar
                |junit-jupiter-params-5.7.2-all.jar
                |junit-jupiter-params-5.7.2.jar
                |junit-platform-commons-1.7.2.jar
                |opentest4j-1.2.0.jar""".trimMargin(),
                root)
        }
    }

    /**
     * Resolved dependencies graph is identical to what Gradle resolves.
     * Be careful: changing of the expected result might rather highlight
     * the error introduced to resolution logic than its improvement while DR evolving.
     */
    @Test
    fun `org_apache_maven maven-core 3_9_6`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            scope = ResolutionScope.COMPILE,
            expected = """root
                |\--- org.apache.maven:maven-core:3.9.6
                |     +--- org.apache.maven:maven-model:3.9.6
                |     |    \--- org.codehaus.plexus:plexus-utils:3.5.1
                |     +--- org.apache.maven:maven-settings:3.9.6
                |     |    \--- org.codehaus.plexus:plexus-utils:3.5.1
                |     +--- org.apache.maven:maven-settings-builder:3.9.6
                |     |    +--- org.apache.maven:maven-builder-support:3.9.6
                |     |    +--- javax.inject:javax.inject:1
                |     |    +--- org.codehaus.plexus:plexus-interpolation:1.26
                |     |    +--- org.codehaus.plexus:plexus-utils:3.5.1
                |     |    +--- org.apache.maven:maven-settings:3.9.6 (*)
                |     |    \--- org.codehaus.plexus:plexus-sec-dispatcher:2.0
                |     |         +--- org.codehaus.plexus:plexus-utils:3.4.1 -> 3.5.1
                |     |         +--- org.codehaus.plexus:plexus-cipher:2.0
                |     |         |    \--- javax.inject:javax.inject:1
                |     |         \--- javax.inject:javax.inject:1
                |     +--- org.apache.maven:maven-builder-support:3.9.6
                |     +--- org.apache.maven:maven-repository-metadata:3.9.6
                |     |    \--- org.codehaus.plexus:plexus-utils:3.5.1
                |     +--- org.apache.maven:maven-artifact:3.9.6
                |     |    +--- org.codehaus.plexus:plexus-utils:3.5.1
                |     |    \--- org.apache.commons:commons-lang3:3.12.0
                |     +--- org.apache.maven:maven-plugin-api:3.9.6
                |     |    +--- org.apache.maven:maven-model:3.9.6 (*)
                |     |    +--- org.apache.maven:maven-artifact:3.9.6 (*)
                |     |    +--- org.eclipse.sisu:org.eclipse.sisu.plexus:0.9.0.M2
                |     |    |    +--- javax.annotation:javax.annotation-api:1.2
                |     |    |    +--- javax.enterprise:cdi-api:1.2
                |     |    |    |    +--- javax.el:javax.el-api:3.0.0
                |     |    |    |    +--- javax.interceptor:javax.interceptor-api:1.2
                |     |    |    |    \--- javax.inject:javax.inject:1
                |     |    |    +--- org.eclipse.sisu:org.eclipse.sisu.inject:0.9.0.M2
                |     |    |    +--- org.codehaus.plexus:plexus-component-annotations:2.1.0
                |     |    |    +--- org.codehaus.plexus:plexus-classworlds:2.6.0 -> 2.7.0
                |     |    |    \--- org.codehaus.plexus:plexus-utils:3.3.0 -> 3.5.1
                |     |    +--- org.codehaus.plexus:plexus-utils:3.5.1
                |     |    \--- org.codehaus.plexus:plexus-classworlds:2.7.0
                |     +--- org.apache.maven:maven-model-builder:3.9.6
                |     |    +--- org.codehaus.plexus:plexus-utils:3.5.1
                |     |    +--- org.codehaus.plexus:plexus-interpolation:1.26
                |     |    +--- javax.inject:javax.inject:1
                |     |    +--- org.apache.maven:maven-model:3.9.6 (*)
                |     |    +--- org.apache.maven:maven-artifact:3.9.6 (*)
                |     |    +--- org.apache.maven:maven-builder-support:3.9.6
                |     |    \--- org.eclipse.sisu:org.eclipse.sisu.inject:0.9.0.M2
                |     +--- org.apache.maven:maven-resolver-provider:3.9.6
                |     |    +--- org.apache.maven:maven-model:3.9.6 (*)
                |     |    +--- org.apache.maven:maven-model-builder:3.9.6 (*)
                |     |    +--- org.apache.maven:maven-repository-metadata:3.9.6 (*)
                |     |    +--- org.apache.maven.resolver:maven-resolver-api:1.9.18
                |     |    +--- org.apache.maven.resolver:maven-resolver-spi:1.9.18
                |     |    |    \--- org.apache.maven.resolver:maven-resolver-api:1.9.18
                |     |    +--- org.apache.maven.resolver:maven-resolver-util:1.9.18
                |     |    |    \--- org.apache.maven.resolver:maven-resolver-api:1.9.18
                |     |    +--- org.apache.maven.resolver:maven-resolver-impl:1.9.18
                |     |    |    +--- org.apache.maven.resolver:maven-resolver-api:1.9.18
                |     |    |    +--- org.apache.maven.resolver:maven-resolver-spi:1.9.18 (*)
                |     |    |    +--- org.apache.maven.resolver:maven-resolver-named-locks:1.9.18
                |     |    |    |    \--- org.slf4j:slf4j-api:1.7.36
                |     |    |    +--- org.apache.maven.resolver:maven-resolver-util:1.9.18 (*)
                |     |    |    \--- org.slf4j:slf4j-api:1.7.36
                |     |    +--- org.codehaus.plexus:plexus-utils:3.5.1
                |     |    \--- javax.inject:javax.inject:1
                |     +--- org.apache.maven.resolver:maven-resolver-impl:1.9.18 (*)
                |     +--- org.apache.maven.resolver:maven-resolver-api:1.9.18
                |     +--- org.apache.maven.resolver:maven-resolver-spi:1.9.18 (*)
                |     +--- org.apache.maven.resolver:maven-resolver-util:1.9.18 (*)
                |     +--- org.apache.maven.shared:maven-shared-utils:3.3.4
                |     |    \--- commons-io:commons-io:2.6
                |     +--- org.eclipse.sisu:org.eclipse.sisu.plexus:0.9.0.M2 (*)
                |     +--- org.eclipse.sisu:org.eclipse.sisu.inject:0.9.0.M2
                |     +--- com.google.inject:guice:5.1.0
                |     |    +--- javax.inject:javax.inject:1
                |     |    +--- aopalliance:aopalliance:1.0
                |     |    \--- com.google.guava:guava:30.1-jre -> 32.0.1-jre
                |     |         +--- com.google.guava:failureaccess:1.0.1
                |     |         +--- com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava
                |     |         +--- com.google.code.findbugs:jsr305:3.0.2
                |     |         +--- org.checkerframework:checker-qual:3.33.0
                |     |         +--- com.google.errorprone:error_prone_annotations:2.18.0
                |     |         \--- com.google.j2objc:j2objc-annotations:2.8
                |     +--- com.google.guava:guava:32.0.1-jre (*)
                |     +--- com.google.guava:failureaccess:1.0.1
                |     +--- javax.inject:javax.inject:1
                |     +--- org.codehaus.plexus:plexus-utils:3.5.1
                |     +--- org.codehaus.plexus:plexus-classworlds:2.7.0
                |     +--- org.codehaus.plexus:plexus-interpolation:1.26
                |     +--- org.codehaus.plexus:plexus-component-annotations:2.1.0
                |     +--- org.apache.commons:commons-lang3:3.12.0
                |     \--- org.slf4j:slf4j-api:1.7.36
            """.trimMargin()
        )

        runBlocking {
            downloadAndAssertFiles(
                """aopalliance-1.0.jar
                    |cdi-api-1.2.jar
                    |checker-qual-3.33.0.jar
                    |commons-io-2.6.jar
                    |commons-lang3-3.12.0.jar
                    |error_prone_annotations-2.18.0.jar
                    |failureaccess-1.0.1.jar
                    |guava-32.0.1-jre.jar
                    |guice-5.1.0.jar
                    |j2objc-annotations-2.8.jar
                    |javax.annotation-api-1.2.jar
                    |javax.el-api-3.0.0.jar
                    |javax.inject-1.jar
                    |javax.interceptor-api-1.2.jar
                    |jsr305-3.0.2.jar
                    |listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar
                    |maven-artifact-3.9.6.jar
                    |maven-builder-support-3.9.6.jar
                    |maven-core-3.9.6.jar
                    |maven-model-3.9.6.jar
                    |maven-model-builder-3.9.6.jar
                    |maven-plugin-api-3.9.6.jar
                    |maven-repository-metadata-3.9.6.jar
                    |maven-resolver-api-1.9.18.jar
                    |maven-resolver-impl-1.9.18.jar
                    |maven-resolver-named-locks-1.9.18.jar
                    |maven-resolver-provider-3.9.6.jar
                    |maven-resolver-spi-1.9.18.jar
                    |maven-resolver-util-1.9.18.jar
                    |maven-settings-3.9.6.jar
                    |maven-settings-builder-3.9.6.jar
                    |maven-shared-utils-3.3.4.jar
                    |org.eclipse.sisu.inject-0.9.0.M2.jar
                    |org.eclipse.sisu.plexus-0.9.0.M2.jar
                    |plexus-cipher-2.0.jar
                    |plexus-classworlds-2.7.0.jar
                    |plexus-component-annotations-2.1.0.jar
                    |plexus-interpolation-1.26.jar
                    |plexus-sec-dispatcher-2.0.jar
                    |plexus-utils-3.5.1.jar
                    |slf4j-api-1.7.36.jar""".trimMargin(),
                root)
        }
    }

    @Test
    fun `kotlin test with junit5`() {
        context().use { context ->
            val root = listOf(
                "org.jetbrains.kotlin:kotlin-test-junit5:1.9.20",
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.20",
                "org.jetbrains.kotlin:kotlin-stdlib-common:1.9.20",
            ).toRootNode(context)
            val resolver = Resolver()
            runBlocking { resolver.buildGraph(root, ResolutionLevel.NETWORK) }
            assertEquals(
                """root
               |+--- org.jetbrains.kotlin:kotlin-test-junit5:1.9.20
               ||    +--- org.jetbrains.kotlin:kotlin-test:1.9.20
               ||    |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.20
               ||    |         \--- org.jetbrains:annotations:13.0
               ||    \--- org.junit.jupiter:junit-jupiter-api:5.6.3
               ||         +--- org.junit:junit-bom:5.6.3
               ||         +--- org.apiguardian:apiguardian-api:1.1.0
               ||         +--- org.opentest4j:opentest4j:1.2.0
               ||         \--- org.junit.platform:junit-platform-commons:1.6.3
               ||              +--- org.junit:junit-bom:5.6.3
               ||              \--- org.apiguardian:apiguardian-api:1.1.0
               |+--- org.jetbrains.kotlin:kotlin-stdlib:1.9.20 (*)
               |\--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.20
               |     \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.20 (*)
        """.trimMargin(),
                root
            )
        }
    }

    @Test
    fun `datetime and kotlin test with junit`() {
        context().use { context ->
            val root = listOf(
                "org.jetbrains.kotlin:kotlin-stdlib:1.9.20",
                "org.jetbrains.kotlinx:kotlinx-datetime:0.4.0",
                "org.jetbrains.kotlin:kotlin-test:1.9.0",
                "org.jetbrains.kotlin:kotlin-test-junit:1.9.20",
                "junit:junit:4.12",
            ).toRootNode(context)
            val resolver = Resolver()
            runBlocking { resolver.buildGraph(root, ResolutionLevel.NETWORK) }
            assertEquals(
                """root
               |+--- org.jetbrains.kotlin:kotlin-stdlib:1.9.20
               ||    +--- org.jetbrains:annotations:13.0
               ||    \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.20 (c)
               |+--- org.jetbrains.kotlinx:kotlinx-datetime:0.4.0
               ||    \--- org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.4.0
               ||         +--- org.jetbrains.kotlin:kotlin-stdlib:1.7.0 -> 1.9.20 (*)
               ||         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.7.0 -> 1.9.20
               ||              \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.20 (*)
               |+--- org.jetbrains.kotlin:kotlin-test:1.9.0 -> 1.9.20
               ||    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.20 (*)
               |+--- org.jetbrains.kotlin:kotlin-test-junit:1.9.20
               ||    +--- org.jetbrains.kotlin:kotlin-test:1.9.20 (*)
               ||    \--- junit:junit:4.13.2
               ||         \--- org.hamcrest:hamcrest-core:1.3
               |\--- junit:junit:4.12 -> 4.13.2 (*)
        """.trimMargin(),
                root
            )
        }
    }

    @Test
    fun `jackson and guava`() {
        context().use { context ->
            val root = listOf(
                "org.antlr:antlr4-runtime:4.7.1",
                "org.abego.treelayout:org.abego.treelayout.core:1.0.3",
                "com.fasterxml.jackson.core:jackson-core:2.9.9",
                "com.fasterxml.jackson.jaxrs:jackson-jaxrs-json-provider:2.9.9",
                "org.apache.commons:commons-lang3:3.9",
                "commons-io:commons-io:2.6",
                "org.reflections:reflections:0.9.8",
                "javax.inject:javax.inject:1",
                "net.openhft:compiler:2.3.4",
            ).toRootNode(context)
            val resolver = Resolver()
            runBlocking { resolver.buildGraph(root, ResolutionLevel.NETWORK) }
            assertEquals(
                """root
            |+--- org.antlr:antlr4-runtime:4.7.1
            |+--- org.abego.treelayout:org.abego.treelayout.core:1.0.3
            |+--- com.fasterxml.jackson.core:jackson-core:2.9.9
            |+--- com.fasterxml.jackson.jaxrs:jackson-jaxrs-json-provider:2.9.9
            ||    +--- com.fasterxml.jackson.jaxrs:jackson-jaxrs-base:2.9.9
            ||    |    +--- com.fasterxml.jackson.core:jackson-core:2.9.9
            ||    |    \--- com.fasterxml.jackson.core:jackson-databind:2.9.9
            ||    |         +--- com.fasterxml.jackson.core:jackson-annotations:2.9.0
            ||    |         \--- com.fasterxml.jackson.core:jackson-core:2.9.9
            ||    \--- com.fasterxml.jackson.module:jackson-module-jaxb-annotations:2.9.9
            ||         +--- com.fasterxml.jackson.core:jackson-annotations:2.9.0
            ||         +--- com.fasterxml.jackson.core:jackson-core:2.9.9
            ||         \--- com.fasterxml.jackson.core:jackson-databind:2.9.9 (*)
            |+--- org.apache.commons:commons-lang3:3.9
            |+--- commons-io:commons-io:2.6
            |+--- org.reflections:reflections:0.9.8
            ||    +--- com.google.guava:guava:11.0.2
            ||    |    \--- com.google.code.findbugs:jsr305:1.3.9
            ||    +--- javassist:javassist:3.12.1.GA
            ||    \--- dom4j:dom4j:1.6.1
            ||         \--- xml-apis:xml-apis:1.0.b2
            |+--- javax.inject:javax.inject:1
            |\--- net.openhft:compiler:2.3.4
            |     +--- org.slf4j:slf4j-api:1.7.25
            |     \--- com.intellij:annotations:12.0
        """.trimMargin(),
                root
            )
        }
    }

    @Test
    fun `org_jetbrains_compose_ui ui-uikit 1_6_10 multiplatform`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            dependency = "org.jetbrains.compose.ui:ui-uikit:1.6.10",
            scope = ResolutionScope.RUNTIME,
            platform = setOf(
                ResolutionPlatform.IOS_ARM64,
                ResolutionPlatform.IOS_X64,
                ResolutionPlatform.IOS_SIMULATOR_ARM64
            ),
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_JETBRAINS_KPM_PUBLIC, REDIRECTOR_MAVEN_GOOGLE),
            expected = """root
                |\--- org.jetbrains.compose.ui:ui-uikit:1.6.10
            """.trimMargin()
        )

        assertFiles(
            """
            ui-uikit-uikitMain-1.6.10.klib
            """.trimIndent(),
            root
        )
    }

    @Test
    fun `org_jetbrains_compose_ui ui-uikit 1_6_10 single platform`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            dependency = "org.jetbrains.compose.ui:ui-uikit:1.6.10",
            scope = ResolutionScope.RUNTIME,
            platform = setOf(ResolutionPlatform.IOS_SIMULATOR_ARM64),
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_JETBRAINS_KPM_PUBLIC, REDIRECTOR_MAVEN_GOOGLE),
            expected = """root
                |\--- org.jetbrains.compose.ui:ui-uikit:1.6.10
                |     \--- org.jetbrains.compose.ui:ui-uikit-uikitsimarm64:1.6.10
            """.trimMargin()
        )

        runBlocking {
            downloadAndAssertFiles(
                """
            ui-uikit-uikitsimarm64-1.6.10-cinterop-utils.klib
            ui-uikit-uikitsimarm64-1.6.10.klib
            """.trimIndent(),
                root
            )
        }
    }

//    @Test
//    fun `jetbrains_ring_bundle bundle-api 2_0_65`(testInfo: TestInfo) {
//        val root = doTestImpl(
//            testInfo,
//            scope = ResolutionScope.RUNTIME,
//            platform = setOf(ResolutionPlatform.JVM),
//            repositories = listOf(Repository("https://packages.jetbrains.team/maven/p/bnd/internal",
//                "Alexey.Barsov",
//                "set-test-password"
//            )),
//            expected = """root
//                |\--- jetbrains.ring.bundle:bundle-api:2.0.65
//                |     \--- org.jetbrains:annotations:13.0
//            """.trimMargin()
//        )
//
//        assertFiles("""
//            annotations-13.0.jar
//            bundle-api-2.0.65.jar
//            """.trimIndent(),
//            root
//        )
//    }



    @Test
    fun `org_jetbrains_compose_material3 material3-uikitarm64 1_6_10`(testInfo: TestInfo) {
        doTest(
            testInfo,
            scope = ResolutionScope.RUNTIME,
            platform = setOf(ResolutionPlatform.IOS_ARM64),
            repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_JETBRAINS_KPM_PUBLIC, REDIRECTOR_MAVEN_GOOGLE),
            expected = """root
               |\--- org.jetbrains.compose.material3:material3-uikitarm64:1.6.10
               |     +--- org.jetbrains.compose.animation:animation-core:1.6.10
               |     |    \--- org.jetbrains.compose.animation:animation-core-uikitarm64:1.6.10
               |     |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10
               |     |         |    \--- androidx.annotation:annotation:1.8.0
               |     |         |         \--- androidx.annotation:annotation-iosarm64:1.8.0
               |     |         |              \--- org.jetbrains.kotlin:kotlin-stdlib:1.7.10 -> 1.9.23
               |     |         +--- org.jetbrains.compose.collection-internal:collection:1.6.10
               |     |         |    \--- androidx.collection:collection:1.4.0
               |     |         |         \--- androidx.collection:collection-iosarm64:1.4.0
               |     |         |              +--- androidx.annotation:annotation:1.7.0 -> 1.8.0 (*)
               |     |         |              \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 1.9.23
               |     |         +--- org.jetbrains.compose.runtime:runtime:1.6.10
               |     |         |    \--- org.jetbrains.compose.runtime:runtime-uikitarm64:1.6.10
               |     |         |         +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
               |     |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23
               |     |         |         |    \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.23
               |     |         |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2
               |     |         |         |    \--- org.jetbrains.kotlinx:atomicfu-iosarm64:0.23.2
               |     |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 1.9.23
               |     |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
               |     |         |              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-iosarm64:1.8.0
               |     |         |                   +--- org.jetbrains.kotlinx:atomicfu:0.23.1 -> 0.23.2 (*)
               |     |         |                   \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 1.9.23
               |     |         +--- org.jetbrains.compose.ui:ui:1.6.10
               |     |         |    \--- org.jetbrains.compose.ui:ui-uikitarm64:1.6.10
               |     |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.0
               |     |         |         |    \--- androidx.lifecycle:lifecycle-common:2.8.0
               |     |         |         |         \--- androidx.lifecycle:lifecycle-common-iosarm64:2.8.0
               |     |         |         |              +--- androidx.annotation:annotation:1.8.0 (*)
               |     |         |         |              +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 1.9.23
               |     |         |         |              +--- org.jetbrains.kotlinx:atomicfu:0.17.0 -> 0.23.2 (*)
               |     |         |         |              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
               |     |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.0
               |     |         |         |    \--- androidx.lifecycle:lifecycle-runtime:2.8.0
               |     |         |         |         \--- androidx.lifecycle:lifecycle-runtime-iosarm64:2.8.0
               |     |         |         |              +--- androidx.annotation:annotation:1.8.0 (*)
               |     |         |         |              +--- androidx.lifecycle:lifecycle-common:2.8.0 (*)
               |     |         |         |              \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 1.9.23
               |     |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.0
               |     |         |         |    \--- org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose-uikitarm64:2.8.0
               |     |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.0 (*)
               |     |         |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.0 (*)
               |     |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
               |     |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
               |     |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.23
               |     |         |         +--- org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.0
               |     |         |         |    \--- androidx.lifecycle:lifecycle-viewmodel:2.8.0
               |     |         |         |         \--- androidx.lifecycle:lifecycle-viewmodel-iosarm64:2.8.0
               |     |         |         |              +--- androidx.annotation:annotation:1.8.0 (*)
               |     |         |         |              +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.22 -> 1.9.23
               |     |         |         |              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 -> 1.8.0 (*)
               |     |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
               |     |         |         +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
               |     |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
               |     |         |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.6.10
               |     |         |         |    \--- org.jetbrains.compose.runtime:runtime-saveable-uikitarm64:1.6.10
               |     |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
               |     |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 (*)
               |     |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10
               |     |         |         |    \--- org.jetbrains.compose.ui:ui-geometry-uikitarm64:1.6.10
               |     |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
               |     |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10
               |     |         |         |         |    \--- org.jetbrains.compose.ui:ui-util-uikitarm64:1.6.10
               |     |         |         |         |         +--- org.jetbrains.compose.ui:ui-uikit:1.6.10
               |     |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-uikit-uikitarm64:1.6.10
               |     |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 (*)
               |     |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 (*)
               |     |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10
               |     |         |         |    \--- org.jetbrains.compose.ui:ui-graphics-uikitarm64:1.6.10
               |     |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
               |     |         |         |         +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
               |     |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
               |     |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
               |     |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10
               |     |         |         |         |    \--- org.jetbrains.compose.ui:ui-unit-uikitarm64:1.6.10
               |     |         |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
               |     |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
               |     |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
               |     |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
               |     |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 (*)
               |     |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
               |     |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 (*)
               |     |         |         |         \--- org.jetbrains.skiko:skiko:0.8.4
               |     |         |         |              \--- org.jetbrains.skiko:skiko-iosarm64:0.8.4
               |     |         |         |                   +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
               |     |         |         |                   +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 1.9.23
               |     |         |         |                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
               |     |         |         +--- org.jetbrains.compose.ui:ui-text:1.6.10
               |     |         |         |    \--- org.jetbrains.compose.ui:ui-text-uikitarm64:1.6.10
               |     |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
               |     |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
               |     |         |         |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.6.10 (*)
               |     |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
               |     |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
               |     |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
               |     |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
               |     |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 (*)
               |     |         |         |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
               |     |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
               |     |         |         |         \--- org.jetbrains.skiko:skiko:0.8.4 (*)
               |     |         |         +--- org.jetbrains.compose.ui:ui-uikit:1.6.10 (*)
               |     |         |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
               |     |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
               |     |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 (*)
               |     |         |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
               |     |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
               |     |         |         \--- org.jetbrains.skiko:skiko:0.8.4 (*)
               |     |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
               |     |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
               |     |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 (*)
               |     |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
               |     |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0 (*)
               |     +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
               |     +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
               |     +--- org.jetbrains.compose.foundation:foundation:1.6.10
               |     |    \--- org.jetbrains.compose.foundation:foundation-uikitarm64:1.6.10
               |     |         +--- org.jetbrains.compose.animation:animation:1.6.10
               |     |         |    \--- org.jetbrains.compose.animation:animation-uikitarm64:1.6.10
               |     |         |         +--- org.jetbrains.compose.animation:animation-core:1.6.10 (*)
               |     |         |         +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10
               |     |         |         |    \--- org.jetbrains.compose.foundation:foundation-layout-uikitarm64:1.6.10
               |     |         |         |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
               |     |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
               |     |         |         |         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
               |     |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
               |     |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 (*)
               |     |         |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
               |     |         |         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
               |     |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.6.10 (*)
               |     |         |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
               |     |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 (*)
               |     |         +--- org.jetbrains.compose.annotation-internal:annotation:1.6.10 (*)
               |     |         +--- org.jetbrains.compose.collection-internal:collection:1.6.10 (*)
               |     |         +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10 (*)
               |     |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
               |     |         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
               |     |         +--- org.jetbrains.compose.ui:ui-text:1.6.10 (*)
               |     |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
               |     |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 (*)
               |     |         +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
               |     |         \--- org.jetbrains.skiko:skiko:0.8.4 (*)
               |     +--- org.jetbrains.compose.foundation:foundation-layout:1.6.10 (*)
               |     +--- org.jetbrains.compose.material:material-icons-core:1.6.10
               |     |    \--- org.jetbrains.compose.material:material-icons-core-uikitarm64:1.6.10
               |     |         +--- org.jetbrains.compose.ui:ui:1.6.10 (*)
               |     |         +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
               |     |         +--- org.jetbrains.compose.ui:ui-unit:1.6.10 (*)
               |     |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 (*)
               |     +--- org.jetbrains.compose.material:material-ripple:1.6.10
               |     |    \--- org.jetbrains.compose.material:material-ripple-uikitarm64:1.6.10
               |     |         +--- org.jetbrains.compose.animation:animation:1.6.10 (*)
               |     |         +--- org.jetbrains.compose.foundation:foundation:1.6.10 (*)
               |     |         +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
               |     |         +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
               |     |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 (*)
               |     +--- org.jetbrains.compose.runtime:runtime:1.6.10 (*)
               |     +--- org.jetbrains.compose.ui:ui-graphics:1.6.10 (*)
               |     +--- org.jetbrains.compose.ui:ui-text:1.6.10 (*)
               |     +--- org.jetbrains.compose.ui:ui-util:1.6.10 (*)
               |     +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.23 (*)
               |     +--- org.jetbrains.kotlinx:atomicfu:0.23.2 (*)
               |     \--- org.jetbrains.kotlinx:kotlinx-datetime:0.5.0
               |          \--- org.jetbrains.kotlinx:kotlinx-datetime-iosarm64:0.5.0
               |               +--- org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2
               |               |    \--- org.jetbrains.kotlinx:kotlinx-serialization-core-iosarm64:1.6.2
               |               |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.9.21 -> 1.9.23 (*)
               |               |         \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 1.9.23
               |               \--- org.jetbrains.kotlin:kotlin-stdlib:1.9.21 -> 1.9.23
            """.trimMargin()
        )
    }

    @Test
    fun `huge dependency graph with reused subgraphs`(testInfo: TestInfo) {
        val root = doTest(
            testInfo,
            dependency = "org.jetbrains.compose.desktop:desktop-jvm-windows-x64:1.5.10",
            expected = """
                root
                \--- org.jetbrains.compose.desktop:desktop-jvm-windows-x64:1.5.10
                     +--- org.jetbrains.compose.desktop:desktop:1.5.10
                     |    \--- org.jetbrains.compose.desktop:desktop-jvm:1.5.10
                     |         +--- org.jetbrains.compose.foundation:foundation:1.5.10
                     |         |    \--- org.jetbrains.compose.foundation:foundation-desktop:1.5.10
                     |         |         +--- org.jetbrains.compose.animation:animation:1.5.10
                     |         |         |    \--- org.jetbrains.compose.animation:animation-desktop:1.5.10
                     |         |         |         +--- org.jetbrains.compose.animation:animation-core:1.5.10
                     |         |         |         |    \--- org.jetbrains.compose.animation:animation-core-desktop:1.5.10
                     |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.5.10
                     |         |         |         |         |    \--- org.jetbrains.compose.runtime:runtime-desktop:1.5.10
                     |         |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21
                     |         |         |         |         |         |    +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |         |         |         |         |         |    \--- org.jetbrains:annotations:13.0 -> 23.0.0
                     |         |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |         |         |         |         |         +--- org.jetbrains.kotlinx:atomicfu:0.17.0
                     |         |         |         |         |         |    \--- org.jetbrains.kotlinx:atomicfu-jvm:0.17.0
                     |         |         |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.6.0 -> 1.8.21 (*)
                     |         |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.6.0 -> 1.8.21
                     |         |         |         |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4 -> 1.7.3
                     |         |         |         |         |              \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.3
                     |         |         |         |         |                   +--- org.jetbrains:annotations:23.0.0
                     |         |         |         |         |                   +--- org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.7.3
                     |         |         |         |         |                   +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.20 -> 1.8.21
                     |         |         |         |         |                   \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.20 -> 1.8.21
                     |         |         |         |         |                        +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |         |         |         |         |                        \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.21
                     |         |         |         |         |                             \--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |         |         |         |         +--- org.jetbrains.compose.ui:ui:1.5.10
                     |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-desktop:1.5.10
                     |         |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |         |         |         |         |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.5.10
                     |         |         |         |         |         |    \--- org.jetbrains.compose.runtime:runtime-saveable-desktop:1.5.10
                     |         |         |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |         |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.5.10
                     |         |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-geometry-desktop:1.5.10
                     |         |         |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |         |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.5.10
                     |         |         |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-util-desktop:1.5.10
                     |         |         |         |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |         |         |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |         |         |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |         |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.5.10
                     |         |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-graphics-desktop:1.5.10
                     |         |         |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |         |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.5.10
                     |         |         |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-unit-desktop:1.5.10
                     |         |         |         |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |         |         |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.5.10 (*)
                     |         |         |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.5.10 (*)
                     |         |         |         |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |         |         |         |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |         |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.5.10 (*)
                     |         |         |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |         |         |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |         |         |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.21 (*)
                     |         |         |         |         |         |         \--- org.jetbrains.skiko:skiko:0.7.85
                     |         |         |         |         |         |              \--- org.jetbrains.skiko:skiko-awt:0.7.85
                     |         |         |         |         |         |                   +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.20 -> 1.8.21 (*)
                     |         |         |         |         |         |                   +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.20 -> 1.8.21 (*)
                     |         |         |         |         |         |                   +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3 (*)
                     |         |         |         |         |         |                   \--- org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.3 (*)
                     |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-text:1.5.10
                     |         |         |         |         |         |    \--- org.jetbrains.compose.ui:ui-text-desktop:1.5.10
                     |         |         |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |         |         |         |         |         |         +--- org.jetbrains.compose.runtime:runtime-saveable:1.5.10 (*)
                     |         |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-graphics:1.5.10 (*)
                     |         |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.5.10 (*)
                     |         |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.5.10 (*)
                     |         |         |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |         |         |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |         |         |         |         |         |         +--- org.jetbrains.kotlinx:atomicfu:0.17.0 (*)
                     |         |         |         |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4 -> 1.7.3 (*)
                     |         |         |         |         |         |         \--- org.jetbrains.skiko:skiko:0.7.85 (*)
                     |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.5.10 (*)
                     |         |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.5.10 (*)
                     |         |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |         |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |         |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.21 (*)
                     |         |         |         |         |         +--- org.jetbrains.kotlinx:atomicfu:0.17.0 (*)
                     |         |         |         |         |         +--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4 -> 1.7.3 (*)
                     |         |         |         |         |         \--- org.jetbrains.skiko:skiko:0.7.85 (*)
                     |         |         |         |         +--- org.jetbrains.compose.ui:ui-unit:1.5.10 (*)
                     |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.5.10 (*)
                     |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |         |         |         |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4 -> 1.7.3 (*)
                     |         |         |         +--- org.jetbrains.compose.foundation:foundation-layout:1.5.10
                     |         |         |         |    \--- org.jetbrains.compose.foundation:foundation-layout-desktop:1.5.10
                     |         |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |         |         |         |         +--- org.jetbrains.compose.ui:ui:1.5.10 (*)
                     |         |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.5.10 (*)
                     |         |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |         |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |         |         |         +--- org.jetbrains.compose.ui:ui:1.5.10 (*)
                     |         |         |         +--- org.jetbrains.compose.ui:ui-geometry:1.5.10 (*)
                     |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.5.10 (*)
                     |         |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |         |         +--- org.jetbrains.compose.foundation:foundation-layout:1.5.10 (*)
                     |         |         +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |         |         +--- org.jetbrains.compose.ui:ui:1.5.10 (*)
                     |         |         +--- org.jetbrains.compose.ui:ui-text:1.5.10 (*)
                     |         |         +--- org.jetbrains.compose.ui:ui-util:1.5.10 (*)
                     |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |         |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |         |         \--- org.jetbrains.skiko:skiko:0.7.85 (*)
                     |         +--- org.jetbrains.compose.material:material:1.5.10
                     |         |    \--- org.jetbrains.compose.material:material-desktop:1.5.10
                     |         |         +--- org.jetbrains.compose.animation:animation:1.5.10 (*)
                     |         |         +--- org.jetbrains.compose.animation:animation-core:1.5.10 (*)
                     |         |         +--- org.jetbrains.compose.foundation:foundation:1.5.10 (*)
                     |         |         +--- org.jetbrains.compose.foundation:foundation-layout:1.5.10 (*)
                     |         |         +--- org.jetbrains.compose.material:material-icons-core:1.5.10
                     |         |         |    \--- org.jetbrains.compose.material:material-icons-core-desktop:1.5.10
                     |         |         |         +--- org.jetbrains.compose.ui:ui:1.5.10 (*)
                     |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |         |         +--- org.jetbrains.compose.material:material-ripple:1.5.10
                     |         |         |    \--- org.jetbrains.compose.material:material-ripple-desktop:1.5.10
                     |         |         |         +--- org.jetbrains.compose.animation:animation:1.5.10 (*)
                     |         |         |         +--- org.jetbrains.compose.foundation:foundation:1.5.10 (*)
                     |         |         |         +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |         |         |         +--- org.jetbrains.compose.ui:ui-util:1.5.10 (*)
                     |         |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |         |         +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |         |         +--- org.jetbrains.compose.ui:ui:1.5.10 (*)
                     |         |         +--- org.jetbrains.compose.ui:ui-text:1.5.10 (*)
                     |         |         +--- org.jetbrains.compose.ui:ui-util:1.5.10 (*)
                     |         |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |         +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |         +--- org.jetbrains.compose.ui:ui:1.5.10 (*)
                     |         +--- org.jetbrains.compose.ui:ui-tooling-preview:1.5.10
                     |         |    \--- org.jetbrains.compose.ui:ui-tooling-preview-desktop:1.5.10
                     |         |         +--- org.jetbrains.compose.runtime:runtime:1.5.10 (*)
                     |         |         \--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |         +--- org.jetbrains.compose.ui:ui-util:1.5.10 (*)
                     |         +--- org.jetbrains.kotlin:kotlin-stdlib:1.8.21 (*)
                     |         +--- org.jetbrains.kotlin:kotlin-stdlib-common:1.8.21
                     |         +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.21 (*)
                     |         \--- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4 -> 1.7.3 (*)
                     \--- org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.7.85
                          \--- org.jetbrains.skiko:skiko-awt:0.7.85 (*)
            """.trimIndent(),
            scope = ResolutionScope.RUNTIME,
        )

        assertFiles(
            """
            animation-core-desktop-1.5.10-sources.jar
            animation-core-desktop-1.5.10.jar
            animation-desktop-1.5.10-sources.jar
            animation-desktop-1.5.10.jar
            annotations-23.0.0-sources.jar
            annotations-23.0.0.jar
            atomicfu-jvm-0.17.0-sources.jar
            atomicfu-jvm-0.17.0.jar
            desktop-jvm-1.5.10-sources.jar
            desktop-jvm-1.5.10.jar
            foundation-desktop-1.5.10-sources.jar
            foundation-desktop-1.5.10.jar
            foundation-layout-desktop-1.5.10-sources.jar
            foundation-layout-desktop-1.5.10.jar
            kotlin-stdlib-1.8.21-sources.jar
            kotlin-stdlib-1.8.21.jar
            kotlin-stdlib-common-1.8.21-sources.jar
            kotlin-stdlib-common-1.8.21.jar
            kotlin-stdlib-jdk7-1.8.21-sources.jar
            kotlin-stdlib-jdk7-1.8.21.jar
            kotlin-stdlib-jdk8-1.8.21-sources.jar
            kotlin-stdlib-jdk8-1.8.21.jar
            kotlinx-coroutines-core-jvm-1.7.3-sources.jar
            kotlinx-coroutines-core-jvm-1.7.3.jar
            material-desktop-1.5.10-sources.jar
            material-desktop-1.5.10.jar
            material-icons-core-desktop-1.5.10-sources.jar
            material-icons-core-desktop-1.5.10.jar
            material-ripple-desktop-1.5.10-sources.jar
            material-ripple-desktop-1.5.10.jar
            runtime-desktop-1.5.10-sources.jar
            runtime-desktop-1.5.10.jar
            runtime-saveable-desktop-1.5.10-sources.jar
            runtime-saveable-desktop-1.5.10.jar
            skiko-awt-0.7.85-sources.jar
            skiko-awt-0.7.85.jar
            skiko-awt-runtime-windows-x64-0.7.85-sources.jar
            skiko-awt-runtime-windows-x64-0.7.85.jar
            ui-desktop-1.5.10-sources.jar
            ui-desktop-1.5.10.jar
            ui-geometry-desktop-1.5.10-sources.jar
            ui-geometry-desktop-1.5.10.jar
            ui-graphics-desktop-1.5.10-sources.jar
            ui-graphics-desktop-1.5.10.jar
            ui-text-desktop-1.5.10-sources.jar
            ui-text-desktop-1.5.10.jar
            ui-tooling-preview-desktop-1.5.10-sources.jar
            ui-tooling-preview-desktop-1.5.10.jar
            ui-unit-desktop-1.5.10-sources.jar
            ui-unit-desktop-1.5.10.jar
            ui-util-desktop-1.5.10-sources.jar
            ui-util-desktop-1.5.10.jar
        """.trimIndent(), root, true
        )
    }

    @Test
    fun `check method distinctBfsSequence`() {
        val repositories = listOf(REDIRECTOR_MAVEN_CENTRAL, REDIRECTOR_DL_GOOGLE_ANDROID).toRepositories()
        val context = context(platform = setOf(ResolutionPlatform.JVM), repositories = repositories)

        val root = DependencyNodeHolder(
            "root",
            listOf(
                DependencyNodeHolder("module1", listOf("androidx.appcompat:appcompat:1.6.1".toMavenNode(context)), context),
                DependencyNodeHolder("module2", listOf("androidx.appcompat:appcompat:1.6.1".toMavenNode(context)), context),
            ),
            context
        )

        val resolver = Resolver()
        runBlocking { resolver.buildGraph(root, ResolutionLevel.NETWORK) }
        root.verifyGraphConnectivity()

        // 2. Checking the absence of duplicates.
        root.distinctBfsSequence()
            .groupBy { it }
            .filter { it.value.size > 1 }
            .let {
                assertTrue(
                    it.isEmpty(),
                    "Duplicated nodes: ${it.keys.map { key -> "${key.key.name}: ${it[key]?.size}" }.toSet()}"
                )
            }

        // 2. Checking the list of distinct nodes sorted alphabetically.
        root.distinctBfsSequence()
            .filterIsInstance<MavenDependencyNode>()
            .sortedBy { it.key.name }
            .let {
                assertEquals(
                    """
                        androidx.activity:activity:1.6.0
                        androidx.activity:activity:1.2.4 -> 1.6.0
                        androidx.annotation:annotation:1.3.0
                        androidx.annotation:annotation:1.1.0 -> 1.3.0
                        androidx.annotation:annotation:1.2.0 -> 1.3.0
                        androidx.annotation:annotation:1.0.0 -> 1.3.0
                        androidx.annotation:annotation-experimental:1.3.0
                        androidx.annotation:annotation-experimental:1.0.0 -> 1.3.0
                        androidx.appcompat:appcompat:1.6.1
                        androidx.appcompat:appcompat-resources:1.6.1
                        androidx.arch.core:core-common:2.1.0
                        androidx.arch.core:core-common:2.0.0 -> 2.1.0
                        androidx.arch.core:core-runtime:2.0.0
                        androidx.collection:collection:1.1.0
                        androidx.collection:collection:1.0.0 -> 1.1.0
                        androidx.core:core:1.9.0
                        androidx.core:core:1.8.0 -> 1.9.0
                        androidx.core:core:1.6.0 -> 1.9.0
                        androidx.core:core:1.0.0 -> 1.9.0
                        androidx.core:core:1.2.0 -> 1.9.0
                        androidx.core:core:1.1.0 -> 1.9.0
                        androidx.core:core-ktx:1.2.0
                        androidx.cursoradapter:cursoradapter:1.0.0
                        androidx.customview:customview:1.0.0
                        androidx.drawerlayout:drawerlayout:1.0.0
                        androidx.fragment:fragment:1.3.6
                        androidx.interpolator:interpolator:1.0.0
                        androidx.lifecycle:lifecycle-common:2.5.1
                        androidx.lifecycle:lifecycle-livedata:2.0.0
                        androidx.lifecycle:lifecycle-livedata-core:2.3.1 -> 2.5.1
                        androidx.lifecycle:lifecycle-livedata-core:2.5.1
                        androidx.lifecycle:lifecycle-livedata-core:2.0.0 -> 2.5.1
                        androidx.lifecycle:lifecycle-runtime:2.5.1
                        androidx.lifecycle:lifecycle-runtime:2.3.1 -> 2.5.1
                        androidx.lifecycle:lifecycle-viewmodel:2.5.1
                        androidx.lifecycle:lifecycle-viewmodel:2.3.1 -> 2.5.1
                        androidx.lifecycle:lifecycle-viewmodel:2.0.0 -> 2.5.1
                        androidx.lifecycle:lifecycle-viewmodel-savedstate:2.5.1
                        androidx.lifecycle:lifecycle-viewmodel-savedstate:2.3.1 -> 2.5.1
                        androidx.loader:loader:1.0.0
                        androidx.savedstate:savedstate:1.2.0
                        androidx.savedstate:savedstate:1.1.0 -> 1.2.0
                        androidx.vectordrawable:vectordrawable:1.1.0
                        androidx.vectordrawable:vectordrawable-animated:1.1.0
                        androidx.versionedparcelable:versionedparcelable:1.1.1
                        androidx.viewpager:viewpager:1.0.0
                        org.jetbrains.kotlin:kotlin-stdlib:1.7.10
                        org.jetbrains.kotlin:kotlin-stdlib:1.6.20 -> 1.7.10
                        org.jetbrains.kotlin:kotlin-stdlib:1.6.21 -> 1.7.10
                        org.jetbrains.kotlin:kotlin-stdlib:1.3.41 -> 1.7.10
                        org.jetbrains.kotlin:kotlin-stdlib:1.6.0 -> 1.7.10
                        org.jetbrains.kotlin:kotlin-stdlib-common:1.7.10
                        org.jetbrains.kotlin:kotlin-stdlib-common:1.6.0 -> 1.7.10
                        org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.6.0
                        org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.0
                        org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.1
                        org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.1
                        org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.1
                        org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.1
                        org.jetbrains:annotations:13.0
                    """.trimIndent(),
                    it.joinToString("\n")
                )
            }
    }

    private fun assertEquals(@Language("text") expected: String, root: DependencyNode) =
        assertEquals(expected, root.prettyPrint().trimEnd())
}
