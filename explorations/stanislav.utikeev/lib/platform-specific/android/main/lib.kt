import org.apache.logging.log4j.kotlin.*
import android.util.Log

actual fun getPlatform(): String = "Android"

actual fun log(string: String) {
    logger("main").info { "Hello, world!" }
    Log.i(string)
}

fun androidSpecificMethod(x: Int): Int = x + 1
