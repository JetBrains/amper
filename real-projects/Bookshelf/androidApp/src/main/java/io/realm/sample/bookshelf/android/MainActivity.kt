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

package io.realm.sample.bookshelf.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import io.realm.sample.bookshelf.android.theme.MyApplicationTheme
import io.realm.sample.bookshelf.android.theme.RallyTheme
import io.realm.sample.bookshelf.android.ui.BookshelfNavHost
import io.realm.sample.bookshelf.android.ui.BookshelfViewModel
import io.realm.sample.bookshelf.android.ui.BottomNavigation
import io.realm.sample.bookshelf.android.ui.NavigationScreen

class MainActivity : ComponentActivity() {

    private val bookshelfViewModel by viewModels<BookshelfViewModel>()

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    color = MaterialTheme.colors.background
                ) {
                    BookShelfApp(bookshelfViewModel)
                }
            }
        }
    }

    @ExperimentalComposeUiApi
    @Composable
    fun BookShelfApp(bookshelfViewModel: BookshelfViewModel) {
        RallyTheme {
            val navController = rememberNavController()
            val navigationItems =
                listOf(NavigationScreen.Search, NavigationScreen.Books, NavigationScreen.About)

            Scaffold(
                bottomBar = {
                    BottomNavigation(navController, navigationItems)
                },
            ) { innerPadding ->
                BookshelfNavHost(
                    bookshelfViewModel,
                    navController,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }

    @ExperimentalComposeUiApi
    @Preview(showBackground = true)
    @Composable
    fun DefaultPreview() {
        MyApplicationTheme {
            BookShelfApp(bookshelfViewModel)
        }
    }
}
