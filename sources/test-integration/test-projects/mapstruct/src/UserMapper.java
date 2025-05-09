/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.mapstruct.Mapper;

@Mapper
public interface UserMapper {
    UserDto map(User user);
}
