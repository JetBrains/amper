/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.maven

import org.jetbrains.amper.dependency.resolution.BaseDRTest
import org.jetbrains.amper.dependency.resolution.JavaVersion
import org.jetbrains.amper.dependency.resolution.MavenDependencyNodeWithContext
import org.jetbrains.amper.dependency.resolution.ResolutionLevel
import org.jetbrains.amper.dependency.resolution.SettingsBuilder
import org.jetbrains.amper.dependency.resolution.diagnostics.CollectingDiagnosticReporter
import org.jetbrains.amper.dependency.resolution.metadata.xml.Project
import org.jetbrains.amper.dependency.resolution.nameToDependency
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.extension.ExtendWith
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import uk.org.webcompere.systemstubs.properties.SystemProperties
import kotlin.io.path.readText
import kotlin.test.DefaultAsserter.fail
import kotlin.test.Test

class PomResolveTest: BaseDRTest() {

    // todo (AB) : What if Maven Profil is both active by default and have additional activation conditions?
    // todo (AB) : see 'com.zaxxer:HikariCP:6.3.2' (profile 'felix')

    /**
     * This test checks that
     * the Maven profile is correctly activated and applied if the required system property is defined.
     *
     * In particular,
     * Maven Profile with id 'forcenpn' declared in parent POM 'io.netty:netty-parent:4.1.124.Final'
     * is activated only if system property 'forcenpn' is defined and has value 'true'.
     *
     * That profile defines the property 'jetty.alpnAgent.option' with the value 'forceNpn=true'
     *
     * <id>forcenpn</id>
     * <activation>
     *   <property>
     *     <name>forcenpn</name>
     *     <value>true</value>
     *   </property>
     * </activation>
     * <properties>
     *   <jetty.alpnAgent.option>forceNpn=true</jetty.alpnAgent.option>
     * </properties>
     */
    @Test
    @ExtendWith(SystemStubsExtension::class)
    fun `maven profile activation condition system property with particular value`(testInfo: TestInfo, systemProperties: SystemProperties) =
        runDrTest {
            val dependency = "io.netty:netty-common:4.1.124.Final"

            // System property 'forcenpn' is not set, => Maven Profile is NOT activated.
            systemProperties.remove("forcenpn")
            withResolvedPom(testInfo = testInfo, dependency = dependency) { project ->
                assertNull(project.properties?.properties["jetty.alpnAgent.option"])
            }
            // System property 'forcenpn' is set to 'XXX', => profile activation condition is still NOT met
            systemProperties.set("forcenpn", "XXX")
            withResolvedPom(testInfo = testInfo, dependency = dependency) { project ->
                assertNull(project.properties?.properties["jetty.alpnAgent.option"])
            }
            // System property 'forcenpn' is set to 'true', => profile is activated, and property 'jetty.alpnAgent.option' is declared
            systemProperties.set("forcenpn", "true")
            withResolvedPom(testInfo = testInfo, dependency = dependency) { project ->
                assertEquals(project.properties?.properties["jetty.alpnAgent.option"],"forceNpn=true")
            }
        }

    /**
     * This test checks that Maven profile is correctly activated and applied if the required system property is defined
     *
     * In particular,
     * Maven Profile with id 'integration-tests' declared in the POM of 'com.amazonaws:aws-java-sdk-pom:1.12.787'
     * is activated only if the system property 'doRelease' is defined (and has any value).
     *
     * That profile defines the property 'checkstyle.skip' with the value 'true'
     *
     * <id>integration-tests</id>
     * <activation>
     *     <property>
     *         <name>doRelease</name>
     *     </property>
     * </activation>
     * <properties>
     *     <checkstyle.skip>true</checkstyle.skip>
     *     ...
     * </properties>
     */
    @Test
    @ExtendWith(SystemStubsExtension::class)
    fun `maven profile activation condition system property with any value`(testInfo: TestInfo, systemProperties: SystemProperties) =
        runDrTest {
            val dependency = "com.amazonaws:aws-java-sdk-pom:1.12.787"

            // System property 'doRelease' is not set, => Maven Profile is NOT activated.
            systemProperties.remove("doRelease")
            withResolvedPom(testInfo = testInfo, dependency = dependency) { project ->
                assertNull(project.properties?.properties["checkstyle.skip"])
            }
            // System property 'doRelease' is set to 'true', => profile is activated, and property 'checkstyle.skip' is declared
            systemProperties.set("doRelease", "true")
            withResolvedPom(testInfo = testInfo, dependency = dependency) { project ->
                assertEquals(project.properties?.properties?.getValue("checkstyle.skip"),"true")
            }
            // System property 'doRelease' is set to 'false', => profile is activated, and property 'checkstyle.skip' is declared
            systemProperties.set("doRelease", "false")
            withResolvedPom(testInfo = testInfo, dependency = dependency) { project ->
                assertEquals(project.properties?.properties?.getValue("checkstyle.skip"),"true")
            }
        }

    /**
     * This test checks that Maven profile is correctly activated and applied if the specific system property is NOT defined
     *
     * In particular,
     * Maven Profile with id 'kotlin' declared in the POM of 'com.esotericsoftware:kryo-parent:5.6.0'
     * is activated if the system property 'skipKotlin' is NOT defined.
     *
     * <id>kotlin</id>
     * <activation>
     * 	 <property>
     * 	   <name>!skipKotlin</name>
     * 	 </property>
     * </activation>
     */
    @Test
    @ExtendWith(SystemStubsExtension::class)
    fun `maven profile activation condition system property is NOT defined`(testInfo: TestInfo, systemProperties: SystemProperties) =
        runDrTest {
            val dependency = "com.esotericsoftware:kryo-parent:5.6.0"

            // System property 'skipKotlin' is not set, => Maven Profile is NOT activated.
            systemProperties.remove("skipKotlin")
            withResolvedPom(testInfo = testInfo, dependency = dependency) { project ->
                assertEquals(project.isActivatedProfile("kotlin"), true)
            }
            // System property 'skipKotlin' is set to 'true', => Maven Profile is activated
            systemProperties.set("skipKotlin", "true")
            withResolvedPom(testInfo = testInfo, dependency = dependency) { project ->
                assertEquals(project.isActivatedProfile("kotlin"), false)
            }
            // System property 'skipKotlin' is set to 'XXX', => Maven Profile is activated (property value doesn't matter)
            systemProperties.set("skipKotlin", "XXX")
            withResolvedPom(testInfo = testInfo, dependency = dependency) { project ->
                assertEquals(project.isActivatedProfile("kotlin"), false)
            }
        }

    /**
     * This test checks that Maven profile is correctly activated and applied
     * if the specific system property either is NOT defined or has value different from declared one
     *
     * In particular,
     * Maven Profile with id 'cpu' declared in the POM of 'org.nd4j:nd4j-backend-impls:1.0.0-beta6'
     * is activated if the system property 'libnd4j.chip' is NOT equal to 'cuda'
     *
     * <activation>
     *   <property>
     *     <name>libnd4j.chip</name>
     *     <value>!cuda</value>
     *   </property>
     * </activation>
     */
    @Test
    @ExtendWith(SystemStubsExtension::class)
    fun `maven profile activation condition system property with negative value`(testInfo: TestInfo, systemProperties: SystemProperties) =
        runDrTest {
            val dependency = "org.nd4j:nd4j-backend-impls:1.0.0-beta6"

            // System property 'libnd4j.chip' is not set, => Maven Profile is activated.
            systemProperties.remove("libnd4j.chip")
            withResolvedPom(testInfo = testInfo, dependency = dependency) { project ->
                assertEquals(project.isActivatedProfile("cpu"), true)
            }
            // System property 'libnd4j.chip' is set to 'XXX', => Maven Profile is activated
            systemProperties.set("libnd4j.chip", "XXX")
            withResolvedPom(testInfo = testInfo, dependency = dependency) { project ->
                assertEquals(project.isActivatedProfile("cpu"), true)
            }
            // System property 'libnd4j.chip' is set to 'cuda', => Maven Profile is NOT activated.
            systemProperties.set("libnd4j.chip", "cuda")
            withResolvedPom(testInfo = testInfo, dependency = dependency) { project ->
                assertEquals(project.isActivatedProfile("cpu"), false)
            }
        }

    /**
     * This test checks that
     * the Maven profile is correctly activated and applied if the required ENV variable is defined.
     *
     * In particular,
     * Maven Profile with id 'travis' declared in parent POM 'commons-codec:commons-codec:1.11'
     * is activated only if ENV variable 'TRAVIS' is set to 'true'.
     *
     * <id>travis</id>
     * <activation>
     *   <property>
     *     <name>env.TRAVIS</name>
     *     <value>true</value>
     *   </property>
     * </activation>
     */
    @Test
    @ExtendWith(SystemStubsExtension::class)
    fun `maven profile activation condition ENV variable with particular value`(testInfo: TestInfo, env: EnvironmentVariables) =
        runDrTest {
            val dependency = "commons-codec:commons-codec:1.11"

            // ENV variable 'TRAVIS' is not set, => Maven Profile is NOT activated.
            env.remove("TRAVIS")
            withResolvedPom(testInfo = testInfo, dependency = dependency) { project ->
                assertEquals(project.isActivatedProfile("travis"), false)
            }
            // ENV variable 'TRAVIS' is set to 'XXX', => profile activation condition is still NOT met
            env.set("TRAVIS", "XXX")
            withResolvedPom(testInfo = testInfo, dependency = dependency) { project ->
                assertEquals(project.isActivatedProfile("travis"), false)
            }
            // ENV variable 'TRAVIS' is set to 'true', => profile is activated
            env.set("TRAVIS", "true")
            withResolvedPom(testInfo = testInfo, dependency = dependency) { project ->
                assertEquals(project.isActivatedProfile("travis"), true)
            }
        }

    /**
     * This test checks
     * that dependency from the active Maven Profile is taken into account
     * if the java version passed to the DR matches activation condition.
     *
     * In particular, pom.xml of the library "org.xmlunit:xmlunit-core:2.10.3"
     * contains the following profile that should be applied if the java version is greater or equal to 9
     *
     * <profile>
     *   <id>java9+</id>
     *   <activation>
     *     <jdk>[9,)</jdk>
     *   </activation>
     *   <dependencies>
     *     <dependency>
     *       <groupId>jakarta.xml.bind</groupId>
     *       <artifactId>jakarta.xml.bind-api</artifactId>
     *     </dependency>
     *     ...
     *   </dependencies>
     * </profile>
     */
    @Test
    fun `maven profile activation condition jdk interval`(testInfo: TestInfo) =
        runDrTest {
            val dependency = "org.xmlunit:xmlunit-core:2.10.3"
            val dependencyFun = fun (project: Project) =
                project.dependencies?.dependencies?.singleOrNull { it.artifactId == "jakarta.xml.bind-api" }

            // jdkVersion 11, => Maven Profile is activated.
            withResolvedPom(testInfo = testInfo, dependency = dependency, jdkVersion = JavaVersion(11)) { project ->
                assertNotNull(dependencyFun(project))
            }
            // jdkVersion 9, => Maven Profile is activated.
            withResolvedPom(testInfo = testInfo, dependency = dependency, jdkVersion = JavaVersion(9)) { project ->
                assertNotNull(dependencyFun(project))
            }
            // jdkVersion 8, => Maven Profile is NOT activated, and dependency on 'jakarta.xml.bind-api' is not added
            withResolvedPom(testInfo = testInfo, dependency = dependency, jdkVersion = JavaVersion(8)) { project ->
                assertNull(dependencyFun(project))
            }
        }

    private suspend fun withResolvedPom(
        testInfo: TestInfo,
        dependency: String = testInfo.nameToDependency(),
        jdkVersion: JavaVersion? = null,
        block: suspend (Project) -> Unit,
    ) {
        val node = doTest(testInfo, dependency = listOf(dependency), jdkVersion = jdkVersion)
        val resolvedMavenDependency = (node.children.single() as MavenDependencyNodeWithContext).dependency
        val pomPath = resolvedMavenDependency.pomPath ?: fail("Path to POM has not been resolved for $dependency")

        val pomText = pomPath.readText()
        val diagnosticsReporter = CollectingDiagnosticReporter()
        val pomProject = resolvedMavenDependency.resolvePom(
            pomText, node.context, ResolutionLevel.NETWORK, diagnosticsReporter
        )

        pomProject
            ?: fail(
                "POM has not been resolved for $dependency \n " +
                        diagnosticsReporter.getMessages().joinToString("\n") { it.message }
            )

        block(pomProject)
    }

    private suspend fun Project.isActivatedProfile(profileName: String) =
        profiles?.profiles?.single { it.id == profileName }?.isActivated(SettingsBuilder().settings)
}