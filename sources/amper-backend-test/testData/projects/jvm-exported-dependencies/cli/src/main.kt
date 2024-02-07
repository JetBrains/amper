fun main() {
    // root() tests that we can access root() from root module via exported dependency in two
    // two() tests that two() can access commons text dependency from root module
    println(root() + " + " + two())
}
