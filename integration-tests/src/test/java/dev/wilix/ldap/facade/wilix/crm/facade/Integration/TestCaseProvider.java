package dev.wilix.ldap.facade.wilix.crm.facade.Integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Класс для загрузки и парсинга тестового кейса.
 */
public class TestCaseProvider implements ArgumentsProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) throws Exception {
        Path routeTestCaseDirectory = Path.of(new ClassPathResource("integration/").getURI());

        return Files.walk(routeTestCaseDirectory).filter(Files::isRegularFile).map(
                testCaseData -> Arguments.of(
                        testCaseData.getFileName().toString(),
                        readTestCase(testCaseData)
                ));
    }

    private static TestCase readTestCase(Path path) {
        TestCase testCase = null;

        try {
            testCase = MAPPER.readValue(path.toFile(), TestCase.class);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return testCase;
    }

}