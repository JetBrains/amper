package org.koin.sample

import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import org.koin.sample.di.appModule
import org.koin.sample.service.UserService

/**
 * Main application class that demonstrates Koin dependency injection.
 *
 * This class is injected with a [UserService] and uses it to manage user data.
 * Upon initialization, it automatically loads the default users into the repository.
 *
 * @property userService The service used for user-related operations, injected by Koin
 */
class UserApplication(
    private val userService: UserService
) {

    /**
     * Initialization block that runs when the application is created.
     * Loads the default users into the repository via the service.
     */
    init {
        println("🚀 Initializing UserApplication...")
        userService.loadUsers()
    }

    /**
     * Prints a greeting message for the specified user.
     * Retrieves the user by name from the service and displays a greeting with their name and email,
     * or an error message if the user is not found.
     *
     * @param name The name of the user to greet
     */
    fun sayHello(name : String) {
        val user = userService.getUserOrNull(name)
        val message = userService.prepareHelloMessage(user)
        println(message)
    }
}

/**
 * Application entry point.
 *
 * Initializes the Koin dependency injection framework with the application module,
 * then retrieves the [UserApplication] instance from Koin and calls its [sayHello] method
 * to demonstrate the dependency injection in action.
 */
fun main() {
    startKoin {
        modules(appModule)
    }

    val userApplication = KoinPlatform.getKoin().get<UserApplication>()
    userApplication.sayHello("Alice")
}