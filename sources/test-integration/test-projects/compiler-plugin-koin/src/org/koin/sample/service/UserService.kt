package org.koin.sample.service

import org.koin.sample.data.DEFAULT_USER
import org.koin.sample.data.DEFAULT_USERS
import org.koin.sample.data.User
import org.koin.sample.repository.UserRepository

/**
 * Service interface for user-related business logic operations.
 * Provides high-level operations for managing and retrieving user data.
 */
interface UserService {
    /**
     * Retrieves a user by name from the repository.
     *
     * @param name The name of the user to retrieve (defaults to the default user's name)
     * @return The [User] if found, or null if no user with the given name exists
     */
    fun getUserOrNull(name : String = DEFAULT_USER.name) : User?

    /**
     * Loads the predefined list of users into the repository.
     */
    fun loadUsers()

    /**
     * Prepares a greeting message for a user.
     *
     * @param user The user to create a greeting for, or null if no user was found
     * @return A formatted greeting message if user exists, or an error message if user is null
     */
    fun prepareHelloMessage(user : User?) : String
}

/**
 * Implementation of [UserService] that coordinates user operations with the repository.
 *
 * @property userRepository The repository used for user data persistence
 */
class UserServiceImpl(
    private val userRepository: UserRepository
) : UserService {

    /**
     * Retrieves a user by name from the repository.
     *
     * @param name The name of the user to search for
     * @return The [User] if found, or null if not found in the repository
     */
    override fun getUserOrNull(name : String) : User? = userRepository.findUserOrNull(name)

    /**
     * Loads all predefined default users into the repository.
     * This method populates the repository with the initial set of users.
     */
    override fun loadUsers() {
        userRepository.addUsers(DEFAULT_USERS)
    }

    /**
     * Prepares a greeting message for a user.
     * Returns a personalized greeting with the user's name and email if the user exists,
     * or an error message if the user is null.
     *
     * @param user The user to create a greeting for
     * @return A formatted greeting string or error message
     */
    override fun prepareHelloMessage(user : User?) : String {
        val message = user?.let { "Hello '${user.name}' (${user.email})! 👋" } ?: "❌ User not found"
        return message
    }
}