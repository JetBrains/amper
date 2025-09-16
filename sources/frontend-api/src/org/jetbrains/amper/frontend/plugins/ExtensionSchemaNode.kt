/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.plugins

import org.jetbrains.amper.frontend.api.SchemaNode

/**
 * Class to instantiate schema nodes that come from plugins that have no distinct internal class to back them.
 */
class ExtensionSchemaNode : SchemaNode()