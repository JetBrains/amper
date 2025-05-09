/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.google.auto.service.AutoService;

@AutoService(Service.class)
public class ServiceImpl implements Service {
    public ServiceImpl() {
    }

    @Override
    public void doSomething() {
        System.out.println("Do something");
    }
}
