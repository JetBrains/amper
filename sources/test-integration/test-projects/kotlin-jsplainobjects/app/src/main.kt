import kotlinx.js.JsPlainObject

@JsPlainObject
external interface User {
    val name: String
    val age: Int
    // You can use nullable types to declare a property as optional
    val email: String?
}

fun main() {
    val user = User(name = "Name", age = 10)
    val user2 = User2(name = "Name2", age = 12)
    val copy = User.copy(user, age = 11, email = "some@user.com")

    println(JSON.stringify(user))
    println(JSON.stringify(user2))
    // { "name": "Name", "age": 10 }
    println(JSON.stringify(copy))
    // { "name": "Name", "age": 11, "email": "some@user.com" }
}
