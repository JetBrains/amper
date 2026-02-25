package org.koin.sample

import org.junit.Test
import org.koin.sample.di.appModule
import org.koin.test.AutoCloseKoinTest
import org.koin.test.verify.verify

/**
 * Test class for verifying the Koin dependency injection module configuration.
 *
 * This test performs a "dry run" verification of the module to ensure:
 * - All dependencies are correctly defined
 * - All constructor parameters can be satisfied
 * - There are no circular dependencies
 * - The module configuration is valid without actually starting the application
 *
 * This test helps catch configuration errors early without needing to run the full application.
 */
class ModuleVerificationTest : AutoCloseKoinTest() {

    /**
     * Verifies that the application module is correctly configured.
     *
     * This test uses Koin's verify() method to check the module configuration
     * for correctness. It will fail if any dependency definitions are invalid
     * or if any required dependencies are missing.
     */
    @Test
    fun verifyModules() {
        appModule.verify()
    }
}