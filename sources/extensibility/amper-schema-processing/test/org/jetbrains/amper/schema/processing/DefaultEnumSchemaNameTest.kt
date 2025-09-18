/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import kotlin.test.Test
import kotlin.test.expect

class DefaultEnumSchemaNameTest {
    @Test
    fun `test constant case conversion`() {
        expect("constant") { "CONSTANT".toKebabCase() }
        expect("my-constant") { "MY_CONSTANT".toKebabCase() }
        expect("my-constant") { "MY__CONSTANT__".toKebabCase() }
        expect("multiple-word-constant") { "MULTIPLE_WORD_CONSTANT".toKebabCase() }
    }

    @Test
    fun `test camel case conversion`() {
        expect("constant") { "Constant".toKebabCase() }
        expect("my-constant") { "MyConstant".toKebabCase() }
        expect("multiple-word-constant") { "MultipleWordConstant".toKebabCase() }
    }

    @Test
    fun `test mixed case and special characters`() {
        expect("my-constant") { "My_Constant".toKebabCase() }
        expect("my-constant-value") { "MY_ConstantValue".toKebabCase() }
        expect("mixed-case-constant") { "MixedCase_CONSTANT".toKebabCase() }
    }

    @Test
    fun `test single word conversion`() {
        expect("constant") { "constant".toKebabCase() }
        expect("constant") { "CONSTANT".toKebabCase() }
        expect("constant") { "Constant".toKebabCase() }
    }
}