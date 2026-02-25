package org.koin.sample.repository

import org.koin.sample.data.User

/**
 * Repository interface for managing user data operations.
 * Provides methods for finding and storing users in the system.
 */
interface UserRepository {
    /**
     * Finds a user by their name.
     *
     * @param name The name of the user to search for
     * @return The [User] if found, or null if no user with the given name exists
     */
    fun findUserOrNull(name : String): User?

    /**
     * Adds multiple users to the repository.
     *
     * @param users The list of users to add to the repository
     */
    fun addUsers(users : List<User>)
}

/**
 * Implementation of [UserRepository] that stores users in memory.
 * Uses an ArrayList to maintain the collection of users.
 */
class UserRepositoryImpl : UserRepository {
    /**
     * Internal mutable list of users stored in memory.
     */
    private val _users = arrayListOf<User>()

    /**
     * Searches for a user by name in the repository.
     * Prints a search message to console for debugging purposes.
     *
     * @param name The name of the user to find
     * @return The matching [User] or null if not found
     */
    override fun findUserOrNull(name: String): User? {
        println("🔍 Searching for user: $name")
        return _users.firstOrNull { it.name == name }
    }

    /**
     * Adds a list of users to the repository.
     * Prints a confirmation message with the number of users added.
     *
     * @param users The list of users to add
     */
    override fun addUsers(users: List<User>) {
        println("💾 Adding ${users.size} users to repository")
        _users.addAll(users)
    }
}