import kotlinx.datetime.toLocalTime

fun main(args: Array<String>) {
    val hour = "12:01:03".toLocalTime().hour
    println(hour)
}
