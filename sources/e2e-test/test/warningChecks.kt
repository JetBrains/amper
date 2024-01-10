import kotlin.test.assertFalse

fun String.checkForWarnings() {

    // Check that AGP uses correct kotlin version for linter.
    assertFalse("Actual:\n$this") { contains("was compiled with an incompatible version of kotlin") }

    // Check that we are not using specified method.
    assertFalse("Actual:\n$this") { contains("`KotlinCompilation.source(KotlinSourceSet)` method is deprecated ") }

    // Do not use default hierarchy.
    assertFalse("Actual:\n$this") { contains("To suppress the 'Default Hierarchy Template' add") }

    // Check that we don't mix source sets from different compilations into one tree.
    assertFalse("Actual:\n$this") { contains("Following Kotlin Source Set groups can't depend on ") }

    assertFalse("Actual:\n$this") { contains("Cant apply multiple settings for application plugin.") }
}