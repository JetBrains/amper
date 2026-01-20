/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven

import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.XmlBufferedReader
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.core.KtXmlReader
import nl.adaptivity.xmlutil.core.impl.multiplatform.InputStream
import nl.adaptivity.xmlutil.serialization.UnknownChildHandler
import nl.adaptivity.xmlutil.serialization.XML

/**
 * Parse maven plugin XML from a passed stream. Stream closing is on the caller side.
 */
@OptIn(ExperimentalXmlUtilApi::class)
fun parseMavenPluginXml(inputStream: InputStream): MavenPluginXml =
    xml.decodeFromReader<MavenPluginXml>(KtXmlReader(inputStream.reader(Charsets.UTF_8)))

@OptIn(ExperimentalXmlUtilApi::class)
private val xml: XML = XML {
    defaultPolicy {
        unknownChildHandler = UnknownChildHandler { reader, _, xmlDesc, _, _ ->
            // Do not recover anything, except `ParameterValue`.
            if (xmlDesc.serialDescriptor != Configuration.serializer().descriptor) return@UnknownChildHandler emptyList()
            // We need to use `peek()` function.
            if (reader !is XmlBufferedReader) return@UnknownChildHandler emptyList()

            @Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
            val replacingReader = object : XmlReader by reader {
                override val localName: String get() = ParameterValue::class.simpleName!!
            }
            val result = buildList {
                while (replacingReader.eventType == EventType.START_ELEMENT) {
                    this += xml.decodeFromReader<ParameterValue>(replacingReader).copy(parameterName = reader.localName)
                    // We need to read next tag to read next potential element if there is the next element.
                    // 2 cases here - either `</configuration>` or the next parameter.
                    //
                    // Also, we can't just use `nextTag()`, because serializer expects the
                    // closing </configuration> tag to be unread yet.
                    do {
                        if (reader.peek()?.eventType == EventType.END_ELEMENT) break
                        replacingReader.next()
                    } while (reader.eventType.isIgnorable)
                }
            }

            // Return data for the recovery.
            listOf(XML.ParsedData(0, result))
        }
    }
}
