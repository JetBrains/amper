/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package com.intellij.icons

import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

object EmptyIcon : Icon {
    override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
    }

    override fun getIconWidth(): Int {
        return 16
    }

    override fun getIconHeight(): Int {
        return 16
    }
}