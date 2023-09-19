package com.intellij.deft.icons

import com.intellij.ui.IconManager
import javax.swing.Icon

object DeftIcons {
    private fun load(path: String, cacheKey: Int, flags: Int): Icon {
        @Suppress("UnstableApiUsage")
        return IconManager.getInstance()
            .loadRasterizedIcon(path, DeftIcons::class.java.getClassLoader(), cacheKey, flags)
    }

    /** 16x16  */
    val FileType: Icon = load("icons/fileType.svg", -7706431, 0)

    /** 16x16  */
    val TemplateFileType: Icon = load("icons/templateFileType.svg", -1569588157, 0)
}
