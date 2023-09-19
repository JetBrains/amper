package com.intellij.deft.codeInsight

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PsiUtilsKtTest : BasePlatformTestCase() {

  fun `test getProduct on short form`() {
    val file = myFixture.configureByText("Pot.yaml", """product: android/app""")
    assertEquals(DeftProduct("app", listOf("android")), file.getProduct())
  }

  fun `test getProduct on long form with array`() {
    val file = myFixture.configureByText("Pot.yaml", """product:
  type: lib
  platforms: [ jvm, android ]""")
    assertEquals(DeftProduct("lib", listOf("jvm", "android")), file.getProduct())
  }

  fun `test getProduct on long form with block`() {
    val file = myFixture.configureByText("Pot.yaml", """product:
  type: lib
  platforms:
    - jvm
    - android""")
    assertEquals(DeftProduct("lib", listOf("jvm", "android")), file.getProduct())
  }

  fun `test getProduct on invalid file`() {
    val file = myFixture.configureByText("Pot.yaml", """product""")
    assertEquals(null, file.getProduct())
  }
}
