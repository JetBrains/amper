/*
 * Copyright 2021 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.sample.bookshelf.android.ui

import androidx.annotation.StringRes
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import io.realm.sample.bookshelf.android.R

sealed class NavigationScreen(
    val name: String,
    @StringRes val resourceId: Int,
    val icon: ImageVector
) {
    companion object {
        fun fromRoute(route: String?): NavigationScreen =
            when (route?.substringBefore("/")) {
                Search.name -> Search
                Books.name -> Books
                About.name -> About
                null -> Search
                else -> throw IllegalArgumentException("Route $route is not recognized.")
            }

        fun isNavigationScreen(route: String?): Boolean =
            when (route) {
                null,
                Search.name,
                Books.name,
                About.name -> true
                else -> false
            }
    }

    object Search : NavigationScreen("Search", R.string.route_search_screen, Icons.Filled.Search)
    object Books : NavigationScreen("Books", R.string.route_books_screen, Icons.Filled.List)
    object About : NavigationScreen("About", R.string.route_about_screen, Icons.Filled.Home)
}

@Composable
fun BottomNavigation(
    navController: NavHostController,
    items: List<NavigationScreen>
) {
    val backstackEntry = navController.currentBackStackEntryAsState()
    val route = backstackEntry.value?.destination?.route

    // Only use navigation bar when receiving a navigation route
    if (NavigationScreen.isNavigationScreen(route)) {
        val currentScreen = NavigationScreen.fromRoute(route)

        BottomNavigation {
            items.forEach { screen ->
                BottomNavigationItem(
                    icon = { Icon(screen.icon, contentDescription = null) },
                    label = { Text(stringResource(id = screen.resourceId)) },
                    selected = currentScreen.name == screen.name,
                    onClick = {
                        // Detect patterns like "A-B-C-B" and pop to and get to A
                        val immediateLoop = navController.currentBackStack.value.map {
                            it.destination.route
                        }.let { routes -> routes[routes.size - 2] }.equals(screen.name)

                        navController.navigate(screen.name) {
                            if (immediateLoop) {
                                popUpTo(screen.name) {
                                    inclusive = true
                                }
                            } else {
                                // Avoid navigating to the same tab
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }
        }
    }
}

@ExperimentalComposeUiApi
@Composable
fun BookshelfNavHost(
    bookshelfViewModel: BookshelfViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController,
        startDestination = NavigationScreen.About.name,
        modifier = modifier
    ) {
        composable(NavigationScreen.Search.name) {
            SearchScreen(
                navController,
                bookshelfViewModel.searchResults,
                bookshelfViewModel.searching,
                { name -> bookshelfViewModel.findBooks(name) }
            )
        }
        // FIXME This doesn't bring saved Books to Realm
        composable(NavigationScreen.Books.name) {
            SavedBooks(
                navController,
                bookshelfViewModel.savedBooks
            )
        }
        composable(NavigationScreen.About.name) {
            AboutScreen()
        }
        composable(
            route = "${DetailsScreen.name}/" +
                    "{${DetailsScreen.ARG_BOOK_ID}}",
            arguments = listOf(
                navArgument(DetailsScreen.ARG_BOOK_ID) {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            backStackEntry.arguments?.also { args ->
                val bookId = args.getString(DetailsScreen.ARG_BOOK_ID)
                    ?: throw IllegalStateException("Book not found")
                DetailsScreen(
                    navController = navController,
                    bookId = bookId,
                    getSavedBooks = bookshelfViewModel.savedBooks,
                    addBook = bookshelfViewModel::addBook,
                    removeBook = bookshelfViewModel::removeBook,
                    getUnsavedBook = bookshelfViewModel::getUnsavedBook
                )
            } ?: throw IllegalStateException("Missing navigation arguments")
        }
    }
}
