/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        System.out.println(Main.class.getClassLoader().getResources("package1/").nextElement().getPath());
    }
}
