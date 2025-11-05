package org.jetbrains.amper.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class ExampleTest {

    @Autowired
    private CustomProperties customProperties;

    @Test
    void customPropertiesAreInjected() {
        assertEquals("hello", customProperties.getValue());
    }
}
