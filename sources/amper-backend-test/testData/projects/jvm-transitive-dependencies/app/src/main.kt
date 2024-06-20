fun main() {
    // fooC1() tests that we can access fooC1() from module C1
    // fooD1() tests that we can access exported module D1
    println(fooC1() /*+ " + " + fooD1()*/)
//    println(fooC1() + " + " + fooC2())  // todo (AB) : fooC2() is not resolved, though it is from exported module.
}
