/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import lombok.Data;

@Data
class Person {
    private String name;
    private int age;
}


public class Main {
    public static void main(String[] args) {
        Person person = new Person();
        person.setName("John");
        person.setAge(25);
        System.out.println(person.getName());
        System.out.println(person.getAge());
    }
}
