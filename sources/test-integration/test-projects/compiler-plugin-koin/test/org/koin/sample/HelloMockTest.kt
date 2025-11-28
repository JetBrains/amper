package org.koin.sample

import org.junit.Rule
import org.junit.Test
import org.koin.core.logger.Level
import org.koin.sample.data.User
import org.koin.sample.di.appModule
import org.koin.sample.repository.UserRepository
import org.koin.sample.service.UserService
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.mock.MockProviderRule
import org.koin.test.mock.declareMock
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.mockito.Mockito.times

/**
 * Test class demonstrating Koin's mocking capabilities with Mockito.
 *
 * This test class shows how to use Koin's testing framework to:
 * - Set up Koin with test modules
 * - Mock dependencies (UserRepository) while keeping the rest of the dependency graph intact
 * - Verify interactions with mocked components
 */
class HelloMockTest : KoinTest {

    /**
     * Koin test rule that initializes the Koin context for testing.
     * Configures debug logging and loads the application module.
     */
    @get:Rule
    val koinTestRule = KoinTestRule.create {
        modules(appModule)
    }

    /**
     * Mock provider rule that configures Mockito as the mocking framework.
     * This rule enables Koin to create mock instances using Mockito.
     */
    @get:Rule
    val mockProvider = MockProviderRule.create { clazz -> Mockito.mock(clazz.java) }

    /**
     * Tests the application with a mocked UserRepository.
     *
     * This test demonstrates:
     * - Declaring a mock for [UserRepository] with stubbed behavior
     * - Running the application with the mocked dependency
     * - Verifying that the mocked repository method was called exactly once
     *
     * The test verifies that [UserRepository.findUserOrNull] is called when
     * [UserApplication.sayHello] is invoked, as the call flows through UserService to UserRepository.
     */
    @Test
    fun `mock test`() {
        val service = declareMock<UserRepository> {
            given(findUserOrNull(anyString())).willReturn(User("Mock","mock@example.com"))
        }

        getKoin().get<UserApplication>().sayHello("Mock")
        Mockito.verify(service,times(1)).findUserOrNull(anyString())
    }
}