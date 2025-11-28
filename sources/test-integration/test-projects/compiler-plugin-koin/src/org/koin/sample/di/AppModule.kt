package org.koin.sample.di

import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single
import org.koin.sample.UserApplication
import org.koin.sample.repository.UserRepository
import org.koin.sample.repository.UserRepositoryImpl
import org.koin.sample.service.UserService
import org.koin.sample.service.UserServiceImpl

/**
 * Main Koin dependency injection module for the application.
 *
 * This module uses the **Koin Compiler Plugin** (`koin-compiler-plugin`) which enables
 * reified generics at compile time. This allows the cleaner `single<T>()` syntax instead
 * of the standard constructor DSL (`singleOf(::ClassName)`).
 *
 * The plugin is applied via `libs.plugins.koin.plugin` in build.gradle.kts and provides
 * the `org.koin.plugin.module.dsl.single` function.
 *
 * Dependencies defined:
 * - [UserApplication] - The main application class (singleton)
 * - [UserRepositoryImpl] - Repository implementation bound to [UserRepository] interface (singleton)
 * - [UserServiceImpl] - Service implementation bound to [UserService] interface (singleton)
 *
 * All dependencies are created as singletons to ensure a single instance
 * throughout the application lifecycle.
 */
val appModule = module {
    single<UserApplication>()
    single<UserRepositoryImpl>() bind UserRepository::class
    single<UserServiceImpl>() bind UserService::class
}