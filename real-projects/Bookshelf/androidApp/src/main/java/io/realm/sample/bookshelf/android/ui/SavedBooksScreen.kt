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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavHostController
import io.realm.sample.bookshelf.android.R
import io.realm.sample.bookshelf.android.theme.horizontalTextPadding
import io.realm.sample.bookshelf.android.theme.rowSize
import io.realm.sample.bookshelf.android.theme.verticalTextPadding
import io.realm.sample.bookshelf.model.Book
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SavedBooks(
    navController: NavHostController,
    savedBooks: StateFlow<List<Book>>
) {
    val books: List<Book> by savedBooks.collectAsState()

    if (books.isEmpty()) {
        Text(
            text = stringResource(id = R.string.search_empty_bookshelf),
            modifier = Modifier
                .padding(
                    top = verticalTextPadding,
                    bottom = verticalTextPadding,
                    start = horizontalTextPadding,
                    end = horizontalTextPadding
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
        )
    } else {
        Column {
            LazyColumn(
                modifier = Modifier.weight(1f),
            ) {
                items(items = books) { book ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(rowSize)
                            .clickable {
                                navController.navigate("${DetailsScreen.name}/${book.title}")
                            }
                    ) {
                        Text(
                            text = book.title,
                            modifier = Modifier
                                .padding(
                                    top = verticalTextPadding,
                                    bottom = verticalTextPadding,
                                    start = horizontalTextPadding,
                                    end = horizontalTextPadding
                                )
                                .weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        }
    }
}
