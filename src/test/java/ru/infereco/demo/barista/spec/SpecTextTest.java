package ru.infereco.demo.barista.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

class SpecTextTest {

    @Test
    void readsDocxDocumentXml(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("карта.docx");
        Files.write(file, fakeDocx("Интерактивная карта. Поиск по названию."));
        assertThat(SpecText.extract(file)).contains("Интерактивная карта");
    }

    @Test
    void extractsHtmlFromConfluenceMime(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("гранты.doc");
        String mime = """
                Date: Tue, 26 May 2026 16:43:07 +0800 (CST)
                Subject: Exported From Confluence
                MIME-Version: 1.0
                Content-Type: multipart/related; boundary="----=_Part_1"

                ------=_Part_1
                Content-Type: text/html; charset=UTF-8
                Content-Transfer-Encoding: quoted-printable

                <p>=D0=93=D1=80=D0=B0=D0=BD=D1=82=D1=8B =D0=9C=D1=8D=D1=80=D0=B0</p>
                ------=_Part_1--
                """;
        Files.writeString(file, mime);
        assertThat(SpecText.extract(file)).contains("Гранты Мэра");
        assertThat(SpecText.isMimeExport(SpecText.extract(file))).isFalse();
    }

    @Test
    void extractsWordMhtmlBase64Utf16(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("faq.doc");
        String html = "<html><p>5.11. Реестр часто задаваемых вопросов</p><p>Кнопка Опубликовать</p></html>";
        byte[] bom = {(byte) 0xFF, (byte) 0xFE};
        byte[] utf16 = html.getBytes(java.nio.charset.StandardCharsets.UTF_16LE);
        byte[] payload = new byte[bom.length + utf16.length];
        System.arraycopy(bom, 0, payload, 0, bom.length);
        System.arraycopy(utf16, 0, payload, bom.length, utf16.length);
        String b64 = java.util.Base64.getEncoder().encodeToString(payload);
        String mime = """
                MIME-Version: 1.0
                Content-Type: multipart/related; boundary="----=_NextPart_X"

                ------=_NextPart_X
                Content-Transfer-Encoding: base64
                Content-Type: text/html; charset="unicode"

                %s
                ------=_NextPart_X--
                """.formatted(b64);
        Files.writeString(file, mime);
        String text = SpecText.extract(file);
        assertThat(text).contains("Реестр часто задаваемых").contains("Опубликовать");
        assertThat(text).doesNotContain("MIME-Version");
        assertThat(SpecText.looksLikeRawMarkup(text)).isFalse();
    }

    @Test
    @EnabledIf("liveSpecExists")
    void extractsLiveWordMhtmlFaq() throws Exception {
        String text = SpecText.extract(liveSpec());
        assertThat(text).contains("Реестр часто задаваемых");
        assertThat(text).doesNotContain("MIME-Version:");
        assertThat(text.length()).isGreaterThan(20_000);
    }

    static boolean liveSpecExists() {
        return Files.isRegularFile(liveSpec());
    }

    private static Path liveSpec() {
        return Path.of("/Users/sparrow/Documents/pto/СП/Системная+постановка_+Проект+АИС+«Платформа+творческого+образования» (2).doc");
    }

    @Test
    void xmlToTextKeepsParagraphs() {
        String xml = "<w:p><w:t>Гранты</w:t></w:p><w:p><w:t>Мэра</w:t></w:p>";
        assertThat(SpecText.xmlToText(xml)).contains("Гранты").contains("Мэра");
    }

    private static byte[] fakeDocx(String text) throws Exception {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(raw)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(("<w:document><w:p><w:t>" + text + "</w:t></w:p></w:document>")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return raw.toByteArray();
    }
}
