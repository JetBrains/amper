/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.metadata.xml

import org.jetbrains.amper.dependency.resolution.nameToDependency
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestInfo
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test

class PomTest {

    @Test
    fun `kotlin-stdlib-1_9_20`(testInfo: TestInfo) = doTest(testInfo)

    @Test
    fun `kotlin-test-1_9_20`(testInfo: TestInfo) = doTest(testInfo)

    @Test
    fun `kotlin-test-junit-1_9_20`(testInfo: TestInfo) = doTest(testInfo)

    @Test
    fun `kotlinx-coroutines-core-1_6_4`(testInfo: TestInfo) = doTest(testInfo)

    @Test
    fun `kotlinx-coroutines-core-jvm-1_6_4`(testInfo: TestInfo) = doTest(testInfo)

    @Test
    fun `core-ktx-1_1_0`(testInfo: TestInfo) = doTest(testInfo) { it.replace("<packaging>aar</packaging>", "") }

    @Test
    fun `junit-4_13_2`(testInfo: TestInfo) = doTest(testInfo) {
        it.replace("<issueManagement>.*</distributionManagement>".toRegex(), "")
            .replace("<build>.*</profiles>".toRegex(), "")
    }

    @Test
    fun `hamcrest-core-1_3`(testInfo: TestInfo) = doTest(testInfo) {
        it.replace("<packaging>jar</packaging>", "")
            .replace("<description> ", "<description>")
    }

    @Test
    fun `jackson-jaxrs-json-provider-2_9_9`(testInfo: TestInfo) = doTest(testInfo) {
        it.replace("<packaging>bundle</packaging>", "")
            .replace("<build>.*</build>".toRegex(), "")
            .replace("\\s+".toRegex(), " ")
            .replace("\" ,", "\",")
    }

    @Test
    fun `commons-lang3-3_9`(testInfo: TestInfo) = doTest(testInfo) {
        it.replace("<modelVersion>4.0.0</modelVersion>", "")
            .replace("<inceptionYear>2001</inceptionYear>", "")
            .replace("<id>.*?</id>".toRegex(), "")
            .replace("<scm>.*</scm>".toRegex(), "")
            .replace("<issueManagement>.*</issueManagement>".toRegex(), "")
            .replace("<distributionManagement>.*</distributionManagement>".toRegex(), "")
            .replace("<build>.*</profiles>".toRegex(), "")
            .replace("<organization />", "<organization></organization>")
            .replace("<email />", "<email></email>")
            .replace("<timezone>.*?</timezone>".toRegex(), "")
            .replace("<properties>.*</properties>".toRegex(), "")
            .replace(">\\s+".toRegex(), ">")
            .replace("\\s+".toRegex(), " ")
    }

    @Test
    fun `javax_inject-1`(testInfo: TestInfo) = doTest(testInfo) {
        it.replace("<project .*?>".toRegex(), "<project>")
            .replace("<packaging>jar</packaging>", "")
            .replace("<version>1</version>", "")
    }

    @Test
    fun `reflections-parent-0_9_8`(testInfo: TestInfo) = doTest(testInfo) {
        it.replace("<packaging>pom</packaging>", "")
            .replace("<modules>.*</modules>".toRegex(), "")
            .replace("<developers>.*</developers>".toRegex(), "")
            .replace("<distributionManagement>.*</distributionManagement>".toRegex(), "")
            .replace("<build>.*</build>".toRegex(), "")
    }

    @Test
    fun `dom4j-1_6_1`(testInfo: TestInfo) = doTest(testInfo) {
        it.replace("<project .*?>".toRegex(), "<project>")
            .replace("<name>dom4j</name>", "")
            .replace("<issueManagement>.*</ciManagement>".toRegex(), "")
            .replace("<mailingLists>.*</mailingLists>".toRegex(), "")
            .replace("<organization><name>MetaStuff.*?</organization>".toRegex(), "")
            .replace("<developers>.*</developers>".toRegex(), "")
            .replace("<distributionManagement>.*</distributionManagement>".toRegex(), "")
            .replace("<build>.*</build>".toRegex(), "")
    }

    private fun doTest(testInfo: TestInfo, sanitizer: (String) -> String = { it }) {
        val text = Path("testData/metadata/xml/pom/${testInfo.nameToDependency()}.pom").readText()
        val project = text.parsePom()
        assertEquals(sanitizer(sanitize(text)), sanitizer(project.serialize()))
    }

    private fun sanitize(text: String) =
        text.replace("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"", "")
            .replace(
                "xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\"",
                ""
            )
            .replace(
                "xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\"",
                ""
            ).replace(
                "xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\"",
                ""
            )
            .replace("https://maven.apache.org/xsd/maven-4.0.0.xsd\"", "")
            // non-greedy (*?) match any symbol ([\S\s]) including new lines
            .replace("<!--[\\S\\s]*?-->".toRegex(), "")
            .replace("<\\?xml version=\"1.0\" encoding=\"UTF-8\"\\?>\\s*".toRegex(), "")
            .replace(">\\s+".toRegex(), ">")
            .replace("\"\\s+".toRegex(), "\"")
            .replace("\\s+".toRegex(), " ")
}
