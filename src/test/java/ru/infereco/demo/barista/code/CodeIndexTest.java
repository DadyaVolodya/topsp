package ru.infereco.demo.barista.code;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeIndexTest {

    @Test
    void indexesRoutesAndSkipsNodeModules(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src/lib");
        Files.createDirectories(src);
        Files.createDirectories(dir.resolve("node_modules/lib"));
        Files.writeString(src.resolve("app-routes.ts"), "export const APP_ROUTES = { home: '/' };");
        Files.writeString(dir.resolve("node_modules/lib/ignore.ts"), "export const NO = 1;");
        Path testDir = dir.resolve("src/test/java");
        Files.createDirectories(testDir);
        Files.writeString(testDir.resolve("FooControllerIntegrationTest.java"), "class FooControllerIntegrationTest {}");

        List<Path> files = CodeIndex.list(List.of(dir));

        assertThat(files).hasSize(1);
        assertThat(CodeIndex.relative(dir, files.getFirst())).contains("app-routes.ts");
        assertThat(CodeIndex.read(files.getFirst())).contains("APP_ROUTES");
    }
}
