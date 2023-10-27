import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class ExampleTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun textChangesWhenButtonIsClicked(){
        rule.setContent {
            App()
        }

        rule.onNodeWithTag("button").assertTextEquals("Hello, World!")
        rule.onNodeWithTag("button").performClick()
        rule.onNodeWithTag("button").assertTextEquals("Hello, Desktop!")
    }
}
