import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeUIViewController

@Composable
fun App() {
    Button({}) {
        Text("Hello")
    }
}

fun ViewController() = ComposeUIViewController { App() }