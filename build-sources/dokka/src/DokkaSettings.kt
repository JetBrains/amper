/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins.dokka

import org.jetbrains.amper.plugins.Configurable
import java.nio.file.Path

@Configurable
interface DokkaSettings {
    /**
     * The set of visibility modifiers that should be documented.
     *
     * This can be used if you want to document `protected`/`internal`/`private` declarations,
     * as well as if you want to exclude public declarations and only document internal API.
     *
     * Dokka's default: `public`
     */
    val documentedVisibilities: List<DocumentedVisibility>?

    /**
     * Built-in HTML web-site generator settings.
     *
     * These are not all that are supported by the plugin.
     * See [here](https://kotlinlang.org/docs/dokka-html.html#configuration-options) if you need to expose others.
     */
    val html: HtmlSettings
}

@Configurable
interface HtmlSettings {
    /**
     * List of paths for image assets to be bundled with documentation.
     * The image assets can have any file extension.
     * For more information, see [Customizing assets](https://kotlinlang.org/docs/dokka-html.html#customize-assets).
     */
    val customAssets: List<Path>?

    /**
     * List of paths for .css stylesheets to be bundled with documentation and used for rendering.
     * For more information, see [Customizing styles](https://kotlinlang.org/docs/dokka-html.html#customize-styles).
     */
    val customStyleSheets: List<Path>?

    /**
     * Path to the directory containing custom HTML templates.
     * For more information, see [Templates](https://kotlinlang.org/docs/dokka-html.html#templates).
     */
    val templatesDir: Path?

    /**
     * The text displayed in the footer.
     */
    val footerMessage: String?
}