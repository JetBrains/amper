import java.util.*
import com.sample.service.*

fun main() {
    val services = ServiceLoader.load(MyService::class.java)
    services.first().greet()
}
