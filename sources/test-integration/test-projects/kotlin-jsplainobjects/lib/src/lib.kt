import kotlinx.js.JsPlainObject

@JsPlainObject
external interface User2 {
    val name: String
    val age: Int
    // You can use nullable types to declare a property as optional
    val email: String?
}
