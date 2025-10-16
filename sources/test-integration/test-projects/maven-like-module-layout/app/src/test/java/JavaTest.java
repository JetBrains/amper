import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JavaTest {


    @Test
    void gettingDataFromTestResource() throws IOException {
        try (var inputStream = getClass().getResourceAsStream("test-input.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }

            assertEquals("Hello Test World!", content.toString());
        }
    }

    @Test
    void helloWrapperTest() {
        assertEquals("Hello World!", FuncKt.helloWrapper());
    }
}
