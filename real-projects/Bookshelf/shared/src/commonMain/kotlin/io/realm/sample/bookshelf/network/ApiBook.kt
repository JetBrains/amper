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

package io.realm.sample.bookshelf.network

import io.realm.kotlin.ext.realmListOf
import io.realm.sample.bookshelf.model.Book
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiBook(
    val subtitle: String? = "",
    val title: String = "",
    @SerialName("edition_count") val editionCount: Int? = null,
    @SerialName("first_publish_year") val firstPublishYear: Int? = null,
    @SerialName("cover_i") val imgId: String? = null,
    @SerialName("author_name") val authors: List<String> = emptyList(),
)

fun ApiBook.toRealmBook(): Book {
    return Book().apply {
        subtitle = this@toRealmBook.subtitle
        title = this@toRealmBook.title
        editionCount = this@toRealmBook.editionCount
        firstPublishYear = this@toRealmBook.firstPublishYear
        imgId = this@toRealmBook.imgId
        authors = realmListOf<String>().apply {
            addAll(this@toRealmBook.authors)
        }
    }
}
