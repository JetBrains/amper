import c1.fooC1
import c2.fooC2
import d1.fooD1

fun main() {
    // fooC1() tests that we can access fooC1() from module C1
    // fooC2() tests that we can access exported module C2_exp
    // fooD1() tests that we can access exported module D1_exp
    println(fooC1() + " + " + fooD1() + " + " + fooC2())
}
