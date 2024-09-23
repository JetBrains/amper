/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gen.Res
import com.example.gen.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.vectorResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun ImagesRes(contentPadding: PaddingValues) {
    Column(
        modifier = Modifier.padding(contentPadding).verticalScroll(rememberScrollState()),
    ) {
        SvgShowcase()
        OutlinedCard(modifier = Modifier.padding(8.dp)) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth().fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    modifier = Modifier.size(100.dp),
                    painter = painterResource(Res.drawable.compose),
                    contentDescription = null
                )
                Text(
                    """
                        Image(
                          painter = painterResource(Res.drawable.compose)
                        )
                    """.trimIndent()
                )
            }
        }
        OutlinedCard(modifier = Modifier.padding(8.dp)) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    modifier = Modifier.size(100.dp),
                    painter = painterResource(Res.drawable.insta_icon),
                    contentDescription = null
                )
                Text(
                    """
                        Image(
                          painter = painterResource(Res.drawable.insta_icon)
                        )
                    """.trimIndent()
                )
            }
        }
        OutlinedCard(modifier = Modifier.padding(8.dp)) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    modifier = Modifier.size(140.dp),
                    bitmap = imageResource(Res.drawable.land),
                    contentDescription = null
                )
                Text(
                    """
                        Image(
                          bitmap = imageResource(Res.drawable.land)
                        )
                    """.trimIndent()
                )
            }
        }
        OutlinedCard(modifier = Modifier.padding(8.dp)) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    modifier = Modifier.size(100.dp),
                    imageVector = vectorResource(Res.drawable.droid_icon),
                    contentDescription = null
                )
                Text(
                    """
                        Image(
                          imageVector = vectorResource(Res.drawable.droid_icon)
                        )
                    """.trimIndent()
                )
            }
        }
        OutlinedCard(modifier = Modifier.padding(8.dp)) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    modifier = Modifier.size(100.dp),
                    painter = painterResource(Res.drawable.compose),
                    contentDescription = null
                )
                Text(
                    """
                        Icon(
                          painter = painterResource(Res.drawable.compose)
                        )
                    """.trimIndent()
                )
            }
        }
        OutlinedCard(modifier = Modifier.padding(8.dp)) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    modifier = Modifier.size(100.dp),
                    painter = painterResource(Res.drawable.insta_icon),
                    contentDescription = null
                )
                Text(
                    """
                        Icon(
                          painter = painterResource(Res.drawable.insta_icon)
                        )
                    """.trimIndent()
                )
            }
        }
        OutlinedCard(modifier = Modifier.padding(8.dp)) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    modifier = Modifier.size(140.dp),
                    bitmap = imageResource(Res.drawable.land),
                    contentDescription = null
                )
                Text(
                    """
                        Icon(
                          bitmap = imageResource(Res.drawable.land)
                        )
                    """.trimIndent()
                )
            }
        }
        OutlinedCard(modifier = Modifier.padding(8.dp)) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    modifier = Modifier.size(100.dp),
                    imageVector = vectorResource(Res.drawable.droid_icon),
                    contentDescription = null
                )
                Text(
                    """
                        Icon(
                          imageVector = vectorResource(Res.drawable.droid_icon)
                        )
                    """.trimIndent()
                )
            }
        }
    }
}

@Composable
expect fun SvgShowcase()