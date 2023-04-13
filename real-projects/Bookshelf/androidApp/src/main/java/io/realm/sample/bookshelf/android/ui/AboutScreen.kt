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

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    Card(
        elevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(Modifier.clickable {
            val browserIntent =
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.github.com/realm/realm-kotlin"))
            context.startActivity(browserIntent)
        }) {
            Text(
                modifier = Modifier.padding(4.dp),
                text = """
                    Demo app using Realm-Kotlin Multiplatform SDK
                                        
                    🎨 UI: using Jetpack compose 
                     ---- Shared ---
                    📡 Network: using Ktor & Kotlinx.serialization
                    💾 Persistence: using Realm Database
                    """.trimIndent()
            )
        }
    }
}
