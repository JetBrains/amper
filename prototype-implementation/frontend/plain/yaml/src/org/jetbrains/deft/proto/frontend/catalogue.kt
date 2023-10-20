package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.core.Result
import org.jetbrains.deft.proto.core.asDeftSuccess
import org.jetbrains.deft.proto.core.deftFailure
import org.jetbrains.deft.proto.core.messages.ProblemReporterContext
import org.jetbrains.deft.proto.core.system.DefaultSystemInfo
import org.jetbrains.deft.proto.frontend.nodes.YamlNode
import org.jetbrains.deft.proto.frontend.nodes.reportNodeError

interface Catalog {

    /**
     * Get dependency notation by key.
     */
    context(ProblemReporterContext)
    fun findInCatalogue(key: String, node: YamlNode): Result<String>

}

open class PredefinedCatalog(
    builder: MutableMap<String, String>.() -> Unit
) : Catalog {
    private val map = buildMap(builder)

    context(ProblemReporterContext)
    override fun findInCatalogue(key: String, node: YamlNode) = map[key]?.let { Result.success(it) }
        ?: run {
            problemReporter.reportNodeError(
                FrontendYamlBundle.message("no.catalog.value", key),
                node
            )
            deftFailure()
        }
}

object BuiltInCatalog : PredefinedCatalog({
    // TODO Pass version from build.
    // Add kotlin-test.
    val kotlinTestVersion = "1.9.20-Beta"
    put("kotlin-test-junit5", "org.jetbrains.kotlin:kotlin-test-junit5:$kotlinTestVersion")
    put("kotlin-test-junit", "org.jetbrains.kotlin:kotlin-test-junit:$kotlinTestVersion")
    put("kotlin-test", "org.jetbrains.kotlin:kotlin-test:$kotlinTestVersion")

    // TODO Pass version from build.
    // Add compose.
    val composeVersion = "1.5.10-beta01"
    put("compose.animation", "org.jetbrains.compose.animation:animation:$composeVersion")
    put("compose.animationGraphics", "org.jetbrains.compose.animation:animation-graphics:$composeVersion")
    put("compose.foundation", "org.jetbrains.compose.foundation:foundation:$composeVersion")
    put("compose.material", "org.jetbrains.compose.material:material:$composeVersion")
    put("compose.material3", "org.jetbrains.compose.material3:material3:$composeVersion")
    put("compose.runtime", "org.jetbrains.compose.runtime:runtime:$composeVersion")
    put("compose.runtimeSaveable", "org.jetbrains.compose.runtime:runtime-saveable:$composeVersion")
    put("compose.ui", "org.jetbrains.compose.ui:ui:$composeVersion")
    put("compose.uiTooling", "org.jetbrains.compose.ui:ui-tooling:$composeVersion")
    put("compose.preview", "org.jetbrains.compose.ui:ui-tooling-preview:$composeVersion")
    put("compose.materialIconsExtended", "org.jetbrains.compose.material:material-icons-extended:$composeVersion")
    put("compose.components.resources", "org.jetbrains.compose.components:components-resources:$composeVersion")
    put("compose.html.svg", "org.jetbrains.compose.html:html-svg:$composeVersion")
    put("compose.html.testUtils", "org.jetbrains.compose.html:html-test-utils:$composeVersion")
    put("compose.html.core", "org.jetbrains.compose.html:html-core:$composeVersion")
    put("compose.desktop.components.splitPane", "org.jetbrains.compose.components:components-splitpane:$composeVersion")
    put("compose.desktop.components.animatedImage", "org.jetbrains.compose.components:components-animatedimage:$composeVersion")
    put("compose.desktop.common", "org.jetbrains.compose.desktop:desktop:$composeVersion")
    put("compose.desktop.linux_x64", "org.jetbrains.compose.desktop:desktop-jvm-linux-x64:$composeVersion")
    put("compose.desktop.linux_arm64", "org.jetbrains.compose.desktop:desktop-jvm-linux-arm64:$composeVersion")
    put("compose.desktop.windows_x64", "org.jetbrains.compose.desktop:desktop-jvm-windows-x64:$composeVersion")
    put("compose.desktop.macos_x64", "org.jetbrains.compose.desktop:desktop-jvm-macos-x64:$composeVersion")
    put("compose.desktop.macos_arm64", "org.jetbrains.compose.desktop:desktop-jvm-macos-arm64:$composeVersion")
    put("compose.desktop.uiTestJUnit4", "org.jetbrains.compose.ui:ui-test-junit4:$composeVersion")
}) {

    private val systemInfo = DefaultSystemInfo

    context(ProblemReporterContext)
    override fun findInCatalogue(key: String, node: YamlNode) = when (key) {
        // Handle os detection as compose do.
        "compose.desktop.currentOs" ->
            "org.jetbrains.compose.desktop:desktop-jvm-${systemInfo.detect().familyArch}".asDeftSuccess()

        else ->
            super.findInCatalogue(key, node)
    }
}