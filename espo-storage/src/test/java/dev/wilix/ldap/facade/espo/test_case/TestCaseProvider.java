package dev.wilix.ldap.facade.espo.test_case;

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
public abstract class TestCaseProvider implements ArgumentsProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String caseDirectory;
    private final Class<? extends TestCase> testCaseClass;

    protected TestCaseProvider(String caseDirectory, Class<? extends TestCase> testCaseClass) {
        this.caseDirectory = caseDirectory;
        this.testCaseClass = testCaseClass;
    }

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) throws Exception {
        Path routeTestCaseDirectory = Path.of(new ClassPathResource(caseDirectory).getURI());

        return Files.walk(routeTestCaseDirectory).filter(Files::isRegularFile).map(
                testCaseData -> Arguments.of(
                        testCaseData.getFileName().toString(),
                        readTestCase(testCaseData)
                ));
    }

    private Object readTestCase(Path path) {
        TestCase testCase = null;

        try {
            testCase = MAPPER.readValue(path.toFile(), testCaseClass);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return testCase;
    }

}