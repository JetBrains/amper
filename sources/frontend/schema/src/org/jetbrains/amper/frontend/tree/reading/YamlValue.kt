/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.jetbrains.yaml.YAMLTokenTypes
import org.jetbrains.yaml.psi.YAMLAlias
import org.jetbrains.yaml.psi.YAMLBlockScalar
import org.jetbrains.yaml.psi.YAMLCompoundValue
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLQuotedText
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLValue

/**
 * A properly sealed abstraction hierarchy around [YAMLValue] (and [YAMLKeyValue.getKey]'s result in some cases)
 * that alleviates some PSI discrepancies:
 * - Correctly associates/fixes type tags with the corresponding constructs in cases that we are interested in
 * - Models [missing][Missing] values explicitly (they may contain a type tag, e.g., incomplete plugin task declaration)
 */
sealed interface YamlValue {
    val psi: PsiElement
    val tag: PsiElement?

    interface Scalar : YamlValue {
        val textValue: String
        val isLiteral: Boolean
    }

    interface Sequence : YamlValue {
        val items: List<YamlValue>
    }

    interface Mapping : YamlValue {
        val keyValues: List<YamlKeyValue>
    }

    interface UnknownCompound : YamlValue

    interface Alias : YamlValue

    interface Missing : YamlValue
}

/**
 * An abstraction around [YAMLKeyValue].
 */
interface YamlKeyValue {
    val psi: YAMLKeyValue
    val key: YamlValue
    val value: YamlValue
}

private class MissingImpl(
    override val psi: PsiElement,
    override val tag: PsiElement?,
) : YamlValue.Missing {
}

fun YamlValue(value: YAMLValue, tag: PsiElement? = value.tag) : YamlValue {
    @Suppress("unused") // mixin override for YamlValue
    open class YAMLValueBased {
        val psi get() = value
        val tag get() = tag  // implicit (mixin) override for YamlValue.tag
    }
    return when (value) {
        is YAMLScalar if value.isLiteral && value.textValue.startsWith("!") -> {
            // e.g. `myTaskName: !com.example.myTaskType` - "!com.example.myTaskType" gets parsed as a scalar value.
            MissingImpl(psi = value, tag = value)
        }
        is YAMLScalar -> object : YamlValue.Scalar, YAMLValueBased() {
            override val textValue = value.textValue
            override val isLiteral get() = value.isLiteral
        }
        is YAMLMapping -> object : YamlValue.Mapping, YAMLValueBased() {
            override val keyValues = value.keyValues.map { YamlKeyValueImpl(it) }
        }
        is YAMLSequence -> object : YamlValue.Sequence, YAMLValueBased() {
            override val items: List<YamlValue> = value.items.map { item ->
                item.value?.let { YamlValue(it) } ?: MissingImpl(
                    psi = item,
                    tag = item.allChildren().find { it.isYamlTag },
                )
            }
        }
        is YAMLCompoundValue -> object : YamlValue.UnknownCompound, YAMLValueBased() {}
        is YAMLAlias -> object : YamlValue.Alias, YAMLValueBased() {}
        else -> error("Not reached: unexpected YAMLValue: $value")
    }
}

private class YamlKeyValueImpl(
    override val psi: YAMLKeyValue,
) : YamlKeyValue {
    override val key: YamlValue
    override val value: YamlValue

    init {
        val keyPsi = psi.key
        val valuePsi = psi.value
        val allChildren = psi.allChildren()

        val keyTag = allChildren.takeWhile { !it.isYamlColon }.find { it.isYamlTag }
        val valueTag = allChildren.dropWhile { !it.isYamlColon }.find { it.isYamlTag }

        key = if (keyPsi != null) when (keyPsi) {
            is YAMLValue -> YamlValue(keyPsi, tag = keyTag)
            else -> ScalarKeyImpl(
                psi = keyPsi,
                tag = keyTag,
                textValue = psi.keyText,
            )
        } else MissingImpl(
            psi = psi,
            tag = keyTag,
        )

        value = valuePsi?.let { YamlValue(it) } ?: MissingImpl(
            psi = psi,
            tag = valueTag,
        )
    }

    private class ScalarKeyImpl(
        override val psi: PsiElement,
        override val tag: PsiElement?,
        override val textValue: String,
    ) : YamlValue.Scalar {
        override val isLiteral: Boolean = psi.text == textValue
    }
}

private val PsiElement.isYamlTag get() = elementType == YAMLTokenTypes.TAG
private val PsiElement.isYamlColon get() = elementType == YAMLTokenTypes.COLON

private val YAMLScalar.isLiteral get() = this !is YAMLQuotedText && this !is YAMLBlockScalar
