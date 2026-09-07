package ru.infereco.demo.barista.code;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CodeChunkerTest {

    @Test
    void methodAtEndOfJavaFileIsIndexed() {
        String text = """
                package demo;
                public class ApplicationService {
                    public void create() {
                        return;
                    }
                    public void validateEditAllowed() {
                        if (status == SUBMITTED) {
                            throw new IllegalStateException();
                        }
                    }
                }
                """;
        List<CodeChunk> chunks = CodeChunker.chunk("aisto-pto-back", "aisto-pto-back/src/ApplicationService.java", text);
        assertThat(chunks.stream().map(CodeChunk::symbol)).contains("ApplicationService", "validateEditAllowed");
        CodeChunk method = chunks.stream().filter(item -> "validateEditAllowed".equals(item.symbol())).findFirst().orElseThrow();
        assertThat(method.symbolType()).isEqualTo("method");
        assertThat(method.startLine()).isGreaterThan(1);
        assertThat(method.text()).contains("SUBMITTED");
    }

    @Test
    void reactExportBecomesComponent() {
        String text = """
                export function ApplicationEditPage() {
                  const canEditApplication = () => status === 'DRAFT';
                  return <button>edit</button>;
                }
                """;
        List<CodeChunk> chunks = CodeChunker.chunk(
                "aisto-front",
                "aisto-front/src/app/application-edit.tsx",
                text);
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.getFirst().symbol()).isEqualTo("ApplicationEditPage");
        assertThat(chunks.getFirst().symbolType()).isEqualTo("component");
    }
}
