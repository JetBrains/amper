import kotlinx.datetime.Clock

fun main() {
    println("[${Clock.System.now().epochSeconds}] Hello, ${World().get()}!")
}
