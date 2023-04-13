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

package io.realm.sample.bookshelf.database

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.ext.query
import io.realm.sample.bookshelf.model.Book
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RealmDatabase {

    val realm: Realm by lazy {
        val configuration = RealmConfiguration.create(schema = setOf(Book::class))
        Realm.open(configuration)
    }

    fun getAllBooks(): List<Book> {
        return realm.query<Book>().find()
    }

    fun getAllBooksAsFlow(): Flow<List<Book>> {
        return realm.query<Book>().asFlow().map { it.list }
    }

    fun getAllBooksAsCommonFlow(): CFlow<List<Book>> {
        return getAllBooksAsFlow().wrap()
    }

    fun getBooksByTitle(title: String): List<Book> {
        return realm.query<Book>("title = $0", title).find()
    }

    fun getBooksByTitleAsFlow(title: String): Flow<List<Book>> {
        return realm.query<Book>("title = $0", title).asFlow().map { it.list }
    }

    fun getBooksByTitleAsCommonFlow(title: String): CFlow<List<Book>> {
        return getBooksByTitleAsFlow(title).wrap()
    }

    fun addBook(book: Book) {
        realm.writeBlocking {
            copyToRealm(book)
        }
    }

    fun deleteBook(title: String) {
        realm.writeBlocking {
            query<Book>("title = $0", title)
                .first()
                .find()
                ?.let { delete(it) }
                ?: throw IllegalStateException("Book not found.")
        }
    }

    fun clearAllBooks() {
        realm.writeBlocking {
            delete(query<Book>())
        }
    }
}
