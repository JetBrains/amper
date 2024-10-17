package com.jetbrains.sample.lib

@MyParcelize
class User(
    val firstName: String,
    val lastName: String,
    val age: Int,
) : MyParcelable
