
package com.example.shared

import kotlin.test.Test
import kotlin.test.assertTrue

class EnclosingClass {

    @Test
    fun enclosingClassTest() {
        println("running EnclosingClass.enclosingClassTest")
    }

    class NestedClass1 {

        @Test
        fun myNestedTest() {
            println("running EnclosingClass.NestedClass1.myNestedTest")
        }
    }

    class NestedClass2 {

        @Test
        fun myNestedTest() {
            println("running EnclosingClass.NestedClass2.myNestedTest")
        }
    }
}