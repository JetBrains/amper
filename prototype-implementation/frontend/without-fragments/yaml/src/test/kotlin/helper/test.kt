package org.jetbrains.deft.proto.frontend.helper

import org.jetbrains.deft.proto.frontend.ParserKtTest
import org.jetbrains.deft.proto.frontend.parseModule
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.fail

context (Path)
internal fun testParse(resourceName: String, init: TestDirectory.() -> Unit = { directory("src") }) {
    val text = ParserKtTest::class.java.getResource("/$resourceName.yaml")?.readText()
        ?: fail("Resource not found")

    project(parent.toFile()) { init() }

    // When
    val module = withBuildFile(toAbsolutePath()) { parseModule(text) }

    // Then
    val resultText = ParserKtTest::class.java.getResource("/$resourceName.result.txt")?.readText()
        ?: fail("Resource not found")
    assertEquals(resultText.replace("{{ userReadableName }}", module.userReadableName), module.prettyPrint())
}