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

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.realm.sample.bookshelf.BookshelfRepository
import io.realm.sample.bookshelf.model.Book
import io.realm.sample.bookshelf.network.ApiBook
import io.realm.sample.bookshelf.network.toRealmBook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookshelfViewModel : ViewModel() {

    private var repository = BookshelfRepository()

    val searchResults: SnapshotStateList<ApiBook> = mutableStateListOf()
    val searching: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val savedBooks: StateFlow<List<Book>> = repository.allBooksAsFlowable()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun findBooks(keyword: String) {
        viewModelScope.launch {
            searching.value = true
            searchResults.clear()
            searchResults.addAll(repository.getBookByTitle(keyword))
            searching.value = false
        }
    }

    fun addBook(book: Book) {
        repository.addToBookshelf(book)
    }

    fun removeBook(bookId: String) {
        repository.removeFromBookshelf(bookId)
    }

    fun getUnsavedBook(bookId: String): Book {
        return searchResults.find { apiBook -> apiBook.title == bookId }
            ?.toRealmBook()
            ?: throw IllegalStateException("Book '$bookId' not found")
    }
}
