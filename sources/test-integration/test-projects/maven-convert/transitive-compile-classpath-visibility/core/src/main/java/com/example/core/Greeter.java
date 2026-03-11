/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package com.example.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class Greeter {

    private final ObjectMapper mapper = new ObjectMapper();

    public String greet(String name) {
        return "Hello, " + name + "!";
    }

    public String greetAsJson(String name) throws JsonProcessingException {
        return mapper.writeValueAsString(Map.of("message", greet(name)));
    }
}
