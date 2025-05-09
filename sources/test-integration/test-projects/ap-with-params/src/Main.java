/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.google.auto.service.AutoService;

import java.util.ServiceLoader;


public class Main {
    public static void main(String[] args) {
        ServiceLoader.load(Service.class).forEach(Service::doSomething);
    }
}
