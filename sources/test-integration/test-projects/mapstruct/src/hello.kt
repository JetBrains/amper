/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.mapstruct.factory.Mappers


fun hello() {
    val mapper = Mappers.getMapper(UserMapper::class.java)
    val user = User("John", "john@email")
    println(mapper.map(user))
}
