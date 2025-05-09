/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package com.example.app;

import com.example.annotation.HelloWorld;
@HelloWorld("Annotation Processor")
public class Main {
    public static void main(String[] args) {
        System.out.println("This is a simple example that uses the HelloWorld annotation.");
        System.out.println("During compilation, the annotation processor should have printed a greeting message.");
    }
}