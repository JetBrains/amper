/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.SchemaValuesVisitor
import org.jetbrains.amper.frontend.api.ValueBase
import org.jetbrains.amper.frontend.schema.Module
import kotlin.reflect.full.isSubclassOf
import kotlin.test.assertNotNull

/**
 * Test visitor that verifies, that traces are set up correctly for every converted node and
 * attribute.
 */
context(ProblemReporterContext)
class TestTraceValidationVisitor: SchemaValuesVisitor() {
  override fun visitNode(it: SchemaNode) {
    with(it) {
      if (it::class !in nonTraceableNodes) {
        assertNotNull(trace, "Trace of the node ${it::class.simpleName} should not be null")
      }
    }
    super.visitNode(it)
  }

  override fun visitValue(it: ValueBase<*>) {
    with(it) {
      if (it.shouldHaveTrace()) {
        assertNotNull(trace, "Trace of the node value ${it.withoutDefault} of type ${it.withoutDefault!!::class.simpleName} should not be null")
      }
    }
    super.visitValue(it)
  }

  private fun ValueBase<*>.shouldHaveTrace() =
    withoutDefault != null
        && nonTraceableValues.none { withoutDefault!!::class.isSubclassOf(it) }
}

private val nonTraceableNodes = setOf(Module::class)

private val nonTraceableValues = setOf(Map::class)