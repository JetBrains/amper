/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
public class Person {
    private String name;
    private int age;
}
