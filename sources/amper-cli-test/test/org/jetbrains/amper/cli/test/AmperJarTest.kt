/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.junit.jupiter.api.Test


/**
 * Inside the test project there is a ClassLoader::getResources method call, which is used by spring-core during
 * the component scan to find bean definitions inside the jar
 *
 * Spring-core assumes that a zip-file which is basically jar but only with manifest is created properly and
 * contains not only file but also directory entries
 *
 * Based on this assumption, inside the method PathMatchingResourcePatternResolver::doFindAllClassPathResources
 * in spring-core to provide behavior exactly as it's written in the specification (look bean definitions in
 * package and subpackages) in the classpath, before traversing to preliminary filter classpath entites,
 * it represents package as a resource (ex. org/springframework/boot/). It turned out that directory entry is also
 *  a resource inside the jar, and spring-core relies on that.
 *
 */
class AmperJarTest: AmperCliTestBase() {
    @Test
    fun `amper jar`() = runSlowTest {
        // in case of fail, NoSuchElementException raised
        runCli(testProject("jar"), "run") 
    }
    
    @Test
    fun `spring boot`() = runSlowTest {
        // in case of fail, spring can't load the context
        runCli(testProject("amper-spring-boot"), "run")
    }
}
