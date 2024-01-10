/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.resolver

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Creates a new instance of [DocumentBuilder] with secure settings to prevent XML external entity attacks (XXE).
 *
 * The method applies the following security features to the DocumentBuilder:
 * - Disallows DTD declarations.
 * - Disallows external general entities.
 * - Disallows external parameter entities.
 * - Disables loading of external DTDs.
 * - Disables XInclude processing.
 * - Disables expanding entity references.
 *
 * It is important to note that the method follows the recommendations from the OWASP XML External Entity Prevention Cheat Sheet.
 *
 * @return A new instance of [DocumentBuilder] with secure settings.
 * @throws IllegalStateException if unable to create the DOM parser.
 */
fun createDocumentBuilder(): DocumentBuilder {
    // from https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html#jaxp-documentbuilderfactory-saxparserfactory-and-dom4j
    val dbf = DocumentBuilderFactory.newDefaultInstance()
    return try {

        // This is the PRIMARY defense. If DTDs (doctype) are disallowed, almost all
        // XML entity attacks are prevented
        // Xerces 2 only - http://xerces.apache.org/xerces2-j/features.html#disallow-doctype-decl
        var feature = "http://apache.org/xml/features/disallow-doctype-decl"
        dbf.setFeature(feature, true)

        // If you can't completely disable DTDs, then at least do the following:
        // Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-general-entities
        // Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-general-entities
        // JDK7+ - http://xml.org/sax/features/external-general-entities
        //This feature has to be used together with the following one, otherwise it will not protect you from XXE for sure
        feature = "http://xml.org/sax/features/external-general-entities"
        dbf.setFeature(feature, false)

        // Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-parameter-entities
        // Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-parameter-entities
        // JDK7+ - http://xml.org/sax/features/external-parameter-entities
        //This feature has to be used together with the previous one, otherwise it will not protect you from XXE for sure
        feature = "http://xml.org/sax/features/external-parameter-entities"
        dbf.setFeature(feature, false)

        // Disable external DTDs as well
        feature = "http://apache.org/xml/features/nonvalidating/load-external-dtd"
        dbf.setFeature(feature, false)

        // and these as well, per Timothy Morgan's 2014 paper: "XML Schema, DTD, and Entity Attacks"
        dbf.isXIncludeAware = false
        dbf.isExpandEntityReferences = false

        // And, per Timothy Morgan: "If for some reason support for inline DOCTYPE is a requirement, then
        // ensure the entity settings are disabled (as shown above) and beware that SSRF attacks
        // (http://cwe.mitre.org/data/definitions/918.html) and denial
        // of service attacks (such as a billion laughs or decompression bombs via "jar:") are a risk."
        dbf.newDocumentBuilder()
    }
    catch (throwable: Throwable) {
        throw IllegalStateException("Unable to create DOM parser", throwable)
    }
}
