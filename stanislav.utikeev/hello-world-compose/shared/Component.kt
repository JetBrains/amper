import androidx.compose.runtime.*
import androidx.compose.material.*
import androidx.compose.ui.*

@Composable
fun HelloButton() {
    Button(modifier = Modifier.align(Alignment.CenterHorizontally),
        onClick = {
            count.value++
        }) {
        Text(if (count.value == 0) "Hello World" else "Clicked ${count.value}!")
    }
}
