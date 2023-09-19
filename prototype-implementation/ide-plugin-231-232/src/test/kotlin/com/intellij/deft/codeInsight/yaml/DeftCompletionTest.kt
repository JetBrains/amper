package com.intellij.deft.codeInsight.yaml

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DeftCompletionTest : BasePlatformTestCase() {

  fun `test completion offers relevant dependency sections for Android`() {
    myFixture.configureByText("Pot.yaml", """product: android/app
<caret>
""")
    myFixture.complete(CompletionType.BASIC)
    checkLookupElements(
      expected = setOf("dependencies", "test-dependencies", "dependencies@android", "test-dependencies@android"),
      unexpected = setOf("dependencies@ios", "test-dependencies@ios"),
    )
  }

  fun `test tab completion properly replaces dependency section with annotator`() {
    myFixture.configureByText("Pot.yaml", """product: android/app
depende<caret>ncies@android:
""")
    myFixture.complete(CompletionType.BASIC)

    myFixture.lookup.currentItem = myFixture.lookupElements!!.find { it.lookupString == "dependencies@android" }
    myFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)

    myFixture.checkResult("""product: android/app
dependencies@android:
""")
  }

  fun `test completion offers platforms and doesn't offer dependency sections after annotator`() {
    myFixture.configureByText("Pot.yaml", """product:
  type: lib
  platforms: [ios, android]

dependencies@<caret>android:
""")
    myFixture.complete(CompletionType.BASIC)
    checkLookupElements(
      expected = setOf("android", "ios"),
      unexpected = setOf(
        "dependencies", "test-dependencies",
        "dependencies@android", "test-dependencies@android",
        "dependencies@ios", "test-dependencies@ios"
      ),
    )
  }

  fun `test completion offer platforms and doesn't offer dependency sections after annotator`() {
    myFixture.configureByText("Pot.yaml", """product:
  type: lib
  platforms: [ios, android]

dependencies@<caret>android:
""")
    myFixture.complete(CompletionType.BASIC)
    checkLookupElements(
      expected = setOf("android", "ios"),
      unexpected = setOf(
        "dependencies", "test-dependencies",
        "dependencies@android", "test-dependencies@android",
        "dependencies@ios", "test-dependencies@ios"
      ),
    )
  }

  fun `test completion offers short form for scope and exported dependency parameters`() {
    myFixture.configureByText("Pot.yaml", """product: android/app
dependencies:
  - io.ktor:ktor-client-core:2.2.0: <caret>
""")
    myFixture.complete(CompletionType.BASIC)
    checkLookupElements(
      expected = setOf("exported", "compile-only", "runtime-only"),
      unexpected = setOf("scope"),
    )
  }

  fun `test completion offers scope and exported dependency parameters`() {
    myFixture.configureByText("Pot.yaml", """product: android/app
dependencies:
  - io.ktor:ktor-client-core:2.2.0:
      <caret>
""")
    myFixture.complete(CompletionType.BASIC)
    checkLookupElements(
      expected = setOf("exported", "scope"),
      unexpected = setOf("compile-only", "runtime-only"),
    )
  }

  fun `test completion offers dependency scopes`() {
    myFixture.configureByText("Pot.yaml", """product: android/app
dependencies:
  - io.ktor:ktor-client-core:2.2.0:
      scope: <caret>
""")
    myFixture.complete(CompletionType.BASIC)
    checkLookupElements(
      expected = setOf("all", "compile-only", "runtime-only"),
    )
  }

  fun `test completion offers boolean values for exported parameter`() {
    myFixture.configureByText("Pot.yaml", """product: android/app
dependencies:
  - io.ktor:ktor-client-core:2.2.0:
      exported: <caret>
""")
    myFixture.complete(CompletionType.BASIC)
    checkLookupElements(
      expected = setOf("true", "false"),
    )
  }

  fun `test completion offers relevant dependencies`() {
    myFixture.configureByText("Pot.yaml", """product: android/app
dependencies:
  - <caret>
""")
    myFixture.complete(CompletionType.BASIC)
    checkLookupElements(
      expected = setOf("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0", "androidx.core:core-ktx:1.10.1"),
      unexpected = setOf("org.jetbrains.compose.desktop:desktop-jvm:1.4.1"),
    )
  }

  private fun checkLookupElements(expected: Set<String> = setOf(), unexpected: Set<String> = setOf()) {
    val lookupElements = myFixture.lookupElements!!.map { it.lookupString }.toSet()
    assertTrue("Expected elements are missing. Expected: $expected, lookup: $lookupElements",
               lookupElements.containsAll(expected))
    assertFalse("Unexpected elements are present. Unexpected: $unexpected, lookup: $lookupElements",
                lookupElements.intersect(unexpected).isNotEmpty())
  }
}
