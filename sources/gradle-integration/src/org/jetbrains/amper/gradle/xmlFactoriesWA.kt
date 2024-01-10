/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle

import javax.xml.stream.XMLEventFactory
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLOutputFactory

/**
 * W/A for service loading conflict between apple plugin
 * and android plugin.
 */
fun adjustXmlFactories() {
    trySetSystemProperty(
        XMLInputFactory::class.qualifiedName!!,
        "com.sun.xml.internal.stream.XMLInputFactoryImpl"
    )
    trySetSystemProperty(
        XMLOutputFactory::class.qualifiedName!!,
        "com.sun.xml.internal.stream.XMLOutputFactoryImpl"
    )
    trySetSystemProperty(
        XMLEventFactory::class.qualifiedName!!,
        "com.sun.xml.internal.stream.events.XMLEventFactoryImpl"
    )
}