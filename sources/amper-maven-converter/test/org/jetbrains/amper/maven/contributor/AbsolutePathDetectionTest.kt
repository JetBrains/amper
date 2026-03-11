/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven.contributor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AbsolutePathDetectionTest {

    @Test
    fun `Unix absolute paths are detected`() {
        assertTrue(containsAbsolutePath("/home/user"))
        assertTrue(containsAbsolutePath("/Users/Anton"))
        assertTrue(containsAbsolutePath("/var/log/app.log"))
        assertTrue(containsAbsolutePath("/opt/app/config.xml"))
        assertTrue(containsAbsolutePath("/tmp/file.zip"))
    }

    @Test
    fun `Unix paths in middle of string are detected`() {
        assertTrue(containsAbsolutePath("loc=/Users/Anton"))
        assertTrue(containsAbsolutePath("output=/home/user/target"))
        assertTrue(containsAbsolutePath("JAVA_HOME=/usr/lib/jvm/java-17"))
        assertTrue(containsAbsolutePath("path=/opt/app/config.xml"))
    }

    @Test
    fun `Arbitrary text with Unix paths is detected`() {
        assertTrue(containsAbsolutePath("download to /tmp/file.zip"))
        assertTrue(containsAbsolutePath("cp /src/a.txt /dst/b.txt"))
        assertTrue(containsAbsolutePath("Using /home/user/downloads as base"))
    }

    @Test
    fun `Windows drive paths are detected`() {
        assertTrue(containsAbsolutePath("C:\\Users"))
        assertTrue(containsAbsolutePath("D:\\Projects"))
        assertTrue(containsAbsolutePath("C:/Users"))
        assertTrue(containsAbsolutePath("d:/temp/file.txt"))
    }

    @Test
    fun `Windows paths in middle of string are detected`() {
        assertTrue(containsAbsolutePath("loc=C:\\temp\\file.txt"))
        assertTrue(containsAbsolutePath("HOME=D:\\Users\\Name"))
        assertTrue(containsAbsolutePath("path=C:/Projects/app"))
    }

    @Test
    fun `Windows UNC paths are detected`() {
        assertTrue(containsAbsolutePath("\\\\server\\share\\folder"))
        assertTrue(containsAbsolutePath("\\\\192.168.1.1\\share"))
    }

    @Test
    fun `File URLs are detected because they contain paths`() {
        assertTrue(containsAbsolutePath("file:///home/user"))
        assertTrue(containsAbsolutePath("file:///C:/Windows"))
        assertTrue(containsAbsolutePath("file:/home/user/file.txt"))
    }

    @Test
    fun `Jar URLs are detected because they contain paths`() {
        assertTrue(containsAbsolutePath("jar:file:/path!/entry"))
        assertTrue(containsAbsolutePath("jar:file:///lib/app.jar!/META-INF"))
    }

    @Test
    fun `Network URLs are NOT detected`() {
        assertFalse(containsAbsolutePath("http://example.com/path"))
        assertFalse(containsAbsolutePath("https://api.com/v1"))
        assertFalse(containsAbsolutePath("ftp://server/files"))
        assertFalse(containsAbsolutePath("ftps://server/files"))
        assertFalse(containsAbsolutePath("mailto:user@example.com"))
        assertFalse(containsAbsolutePath("data://something"))
    }

    @Test
    fun `Relative paths are NOT detected`() {
        assertFalse(containsAbsolutePath("src/main/java"))
        assertFalse(containsAbsolutePath("target/classes"))
        assertFalse(containsAbsolutePath("./relative/path"))
        assertFalse(containsAbsolutePath("../parent/path"))
        assertFalse(containsAbsolutePath("C:this\\is\\relative"))
        assertFalse(containsAbsolutePath("C:some/relative"))
    }

    @Test
    fun `Windows drive letter embedded in word is NOT detected`() {
        assertFalse(containsAbsolutePath("artifactC:\\path"))
    }

    @Test
    fun `Triple backslash prefix is NOT detected as UNC path`() {
        assertFalse(containsAbsolutePath("\\\\\\server\\share"))
    }

    @Test
    fun `Double slash comments are NOT detected`() {
        assertFalse(containsAbsolutePath("//comment"))
        assertFalse(containsAbsolutePath("// this is a comment"))
    }

    @Test
    fun `Version strings are NOT detected`() {
        assertFalse(containsAbsolutePath("1.0.0"))
        assertFalse(containsAbsolutePath("2.3.4-SNAPSHOT"))
        assertFalse(containsAbsolutePath("version=1.2.3"))
    }

    @Test
    fun `Maven coordinates are NOT detected`() {
        assertFalse(containsAbsolutePath("org.example:artifact:1.0"))
        assertFalse(containsAbsolutePath("com.google.guava:guava:31.1-jre"))
    }

    @Test
    fun `Mixed content with network URL and path detects the path`() {
        assertTrue(containsAbsolutePath("download from http://example.com to /home/user/downloads"))
        assertFalse(containsAbsolutePath("download from http://example.com"))
        assertTrue(containsAbsolutePath("fetch https://api.com/data and save to /tmp/data.json"))
    }

    @Test
    fun `Empty and blank strings are NOT detected`() {
        assertFalse(containsAbsolutePath(""))
        assertFalse(containsAbsolutePath("   "))
    }

    @Test
    fun `Simple strings without paths are NOT detected`() {
        assertFalse(containsAbsolutePath("hello world"))
        assertFalse(containsAbsolutePath("enabled"))
        assertFalse(containsAbsolutePath("true"))
        assertFalse(containsAbsolutePath("some-artifact-id"))
    }
}
