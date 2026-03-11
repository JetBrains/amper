package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.collect.ImmutableList;

/**
 * This class uses dependencies that come transitively from deps-pom module.
 * The deps-pom module has type=pom and scope=compile, so its dependencies
 * (slf4j-api and guava) should be available here.
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        ImmutableList<String> list = ImmutableList.of("Hello", "from", "pom-dependency-type", "test");
        logger.info("Message: {}", String.join(" ", list));
    }
}