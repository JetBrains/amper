/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gen.Res
import com.example.gen.sailing
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun SvgShowcase() {
    OutlinedCard(modifier = Modifier.padding(8.dp)) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                modifier = Modifier.size(100.dp),
                painter = painterResource(Res.drawable.sailing),
                contentDescription = null
            )
            Text(
                """
                    Image(
                      painter = painterResource(Res.drawable.sailing)
                    )
                """.trimIndent()
            )
        }
    }
}