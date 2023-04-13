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

package io.realm.sample.bookshelf.model

import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.ext.realmListOf

class Book : RealmObject {

    var subtitle: String? = ""
    var title: String = ""
    var editionCount: Int? = null
    var firstPublishYear: Int? = null
    var imgId: String? = null
    var authors: RealmList<String> = realmListOf()

    companion object {
        private const val BOOK_COVER_URL = "https://covers.openlibrary.org/b/id/"
    }

    fun getImageURL(): String {
        return "$BOOK_COVER_URL$imgId-M.jpg"
    }
}
