import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

fun main() {
    // check that main common dependency is accessible in JVM sources
    val year = Clock.System.todayIn(TimeZone.currentSystemDefault()).year
    println("It's $year today")
}
