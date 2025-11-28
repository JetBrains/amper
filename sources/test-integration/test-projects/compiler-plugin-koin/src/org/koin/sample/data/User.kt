package org.koin.sample.data

/**
 * Domain Model representing a user in the system.
 *
 * @property name The user's display name
 * @property email The user's email address
 */
data class User(val name: String, val email: String)

/**
 * The default user instance used throughout the application.
 * Represents Alice as the primary user.
 */
val DEFAULT_USER = User("Alice", "alice@example.com")

/**
 * A predefined list of sample users for testing and demonstration purposes.
 * Includes the default user (Alice) and additional users (Bob, Charlie).
 */
val DEFAULT_USERS = listOf(
    DEFAULT_USER,
    User("Bob", "bob@example.com"),
    User("Charlie", "charlie@example.com")
)