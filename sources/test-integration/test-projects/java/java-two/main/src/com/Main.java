package com;

import org.JavaWorld;
import org.World;

public class Main {

    public static void main(String[] args) {
        System.out.println("STDOUT: ");
        World w = new JavaWorld();
        System.out.println("Hello, " + w.getName() + "!");
    }
}
