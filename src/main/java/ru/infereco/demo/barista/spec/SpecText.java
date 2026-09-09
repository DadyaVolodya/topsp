package ru.infereco.demo.barista.spec;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Текст из СП: docx через zip, doc через macOS textutil, txt/md как есть. */
public final class SpecText {

    private static final int MAX_CHARS = 2_000_000;

    private SpecText() {
    }

    public static boolean supported(Path file) {
        String name = file == null || file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".docx") || name.endsWith(".doc") || name.endsWith(".txt") || name.endsWith(".md");
    }

    public static String extract(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            return "";
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        String text;
        if (name.endsWith(".txt") || name.endsWith(".md")) {
            text = decode(Files.readAllBytes(file));
        } else if (name.endsWith(".docx")) {
            text = fromDocx(Files.readAllBytes(file));
            if (text.isBlank()) {
                text = fromTextutil(file);
            }
        } else if (name.endsWith(".doc")) {
            byte[] bytes = Files.readAllBytes(file);
            if (isZip(bytes)) {
                text = fromDocx(bytes);
            } else {
                String raw = decode(bytes);
                text = isMimeExport(raw) ? fromMimeHtml(raw) : fromTextutil(file);
            }
        } else {
            return "";
        }
        text = normalize(text);
        if (looksLikeRawMarkup(text)) {
            return "";
        }
        if (text.length() > MAX_CHARS) {
            return text.substring(0, MAX_CHARS);
        }
        return text;
    }

    /** Единый вид текста СП: без \\r, без хвостовых пробелов, без нулей. */
    public static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u0000', ' ')
                .replaceAll("[ \\t]+", " ")
                .strip();
    }

    static String fromDocx(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 4) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    out.append(xmlToText(new String(zip.readAllBytes(), StandardCharsets.UTF_8)));
                    break;
                }
            }
        }
        return out.toString().trim();
    }

    static boolean isMimeExport(String text) {
        if (text == null || text.length() < 40) {
            return false;
        }
        String head = text.substring(0, Math.min(text.length(), 400)).toLowerCase(Locale.ROOT);
        return head.contains("exported from confluence")
                || head.contains("content-type: multipart")
                || head.contains("mime-version:");
    }

    static boolean looksLikeRawMarkup(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String head = text.substring(0, Math.min(text.length(), 900)).toLowerCase(Locale.ROOT);
        if (head.contains("mime-version:")
                || head.contains("content-transfer-encoding")
                || head.contains("<w:document")
                || head.contains("xmlns:w=")) {
            return true;
        }
        int cyr = 0;
        int limit = Math.min(text.length(), 4000);
        for (int i = 0; i < limit; i++) {
            char c = text.charAt(i);
            if (c >= 'А' && c <= 'я' || c == 'ё' || c == 'Ё') {
                cyr += 1;
            }
        }
        return cyr < 12 && text.length() > 240;
    }

    static String fromMimeHtml(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        HtmlPart part = htmlPart(raw);
        if (part.body.isBlank()) {
            return "";
        }
        byte[] decoded = decodeTransfer(part.body, part.encoding);
        String html = decodeHtml(decoded, part.charset);
        return htmlToText(html);
    }

    static String htmlToText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String noBlocks = html
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)</div>", "\n")
                .replaceAll("(?i)</h[1-6]>", "\n")
                .replaceAll("(?i)</li>", "\n")
                .replaceAll("<[^>]+>", " ");
        return noBlocks
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    static String quotedPrintableUtf8(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        byte[] bytes = raw.getBytes(StandardCharsets.US_ASCII);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == '=' && i + 1 < bytes.length) {
                byte next = bytes[i + 1];
                if (next == '\n') {
                    i += 1;
                    continue;
                }
                if (next == '\r') {
                    i += (i + 2 < bytes.length && bytes[i + 2] == '\n') ? 2 : 1;
                    continue;
                }
                if (i + 2 < bytes.length) {
                    int hi = hex(bytes[i + 1]);
                    int lo = hex(bytes[i + 2]);
                    if (hi >= 0 && lo >= 0) {
                        out.write((hi << 4) | lo);
                        i += 2;
                        continue;
                    }
                }
            }
            out.write(bytes[i]);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static HtmlPart htmlPart(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        int type = lower.indexOf("content-type: text/html");
        if (type < 0) {
            return new HtmlPart("", "utf-8", "");
        }
        int partStart = Math.max(0, raw.lastIndexOf("------=", type));
        int headersEnd = blankLine(raw, type);
        if (headersEnd < 0) {
            return new HtmlPart("", "utf-8", "");
        }
        String headers = raw.substring(partStart, headersEnd);
        int start = headersEnd + (raw.startsWith("\r\n\r\n", headersEnd) ? 4 : 2);
        int end = raw.indexOf("------=", start);
        if (end < 0) {
            end = raw.length();
        }
        return new HtmlPart(
                headerValue(headers, "content-transfer-encoding"),
                charsetOf(headers),
                raw.substring(start, end).trim());
    }

    private static int blankLine(String raw, int from) {
        int lf = raw.indexOf("\n\n", from);
        int crlf = raw.indexOf("\r\n\r\n", from);
        if (lf < 0) {
            return crlf;
        }
        if (crlf >= 0 && crlf < lf) {
            return crlf;
        }
        return lf;
    }

    private static String headerValue(String headers, String name) {
        String lower = headers.toLowerCase(Locale.ROOT);
        int at = lower.indexOf(name.toLowerCase(Locale.ROOT) + ":");
        if (at < 0) {
            return "";
        }
        int start = at + name.length() + 1;
        int end = headers.indexOf('\n', start);
        if (end < 0) {
            end = headers.length();
        }
        return headers.substring(start, end).replace('\r', ' ').trim();
    }

    private static String charsetOf(String headers) {
        String type = headerValue(headers, "content-type").toLowerCase(Locale.ROOT);
        int at = type.indexOf("charset=");
        if (at < 0) {
            return "utf-8";
        }
        String value = type.substring(at + 8).replace("\"", "").replace("'", "").trim();
        int semi = value.indexOf(';');
        if (semi >= 0) {
            value = value.substring(0, semi).trim();
        }
        return value.isBlank() ? "utf-8" : value;
    }

    private static byte[] decodeTransfer(String body, String encoding) {
        String mode = encoding == null ? "" : encoding.toLowerCase(Locale.ROOT);
        if (mode.contains("base64")) {
            String compact = body.replaceAll("\\s+", "");
            try {
                return Base64.getDecoder().decode(compact);
            } catch (IllegalArgumentException ex) {
                return Base64.getMimeDecoder().decode(body);
            }
        }
        if (mode.contains("quoted-printable") || body.contains("=\n") || body.contains("=\r\n")) {
            return quotedPrintableUtf8(body).getBytes(StandardCharsets.UTF_8);
        }
        return body.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String decodeHtml(byte[] bytes, String charsetName) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        Charset charset = charsetOfName(charsetName);
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
            charset = StandardCharsets.UTF_16LE;
        } else if (bytes.length >= 2 && bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
            charset = StandardCharsets.UTF_16BE;
        }
        return new String(bytes, charset);
    }

    private static Charset charsetOfName(String name) {
        String value = name == null ? "" : name.toLowerCase(Locale.ROOT).trim();
        if (value.equals("unicode") || value.equals("utf-16") || value.equals("utf16") || value.equals("utf-16le")) {
            return StandardCharsets.UTF_16LE;
        }
        if (value.equals("utf-16be")) {
            return StandardCharsets.UTF_16BE;
        }
        if (value.equals("windows-1251") || value.equals("cp1251")) {
            return Charset.forName("windows-1251");
        }
        try {
            return value.isBlank() ? StandardCharsets.UTF_8 : Charset.forName(name);
        } catch (Exception ex) {
            return StandardCharsets.UTF_8;
        }
    }

    private static boolean isZip(byte[] bytes) {
        return bytes != null && bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K';
    }

    private record HtmlPart(String encoding, String charset, String body) {
    }

    private static int hex(byte value) {
        char c = (char) value;
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        return -1;
    }

    static String xmlToText(String xml) {
        if (xml == null || xml.isBlank()) {
            return "";
        }
        String noTags = xml.replaceAll("</w:p>", "\n").replaceAll("<[^>]+>", " ");
        return noTags.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"");
    }

    private static String fromTextutil(Path file) {
        try {
            Process process = new ProcessBuilder("textutil", "-stdout", "-convert", "txt", file.toString())
                    .redirectErrorStream(true)
                    .start();
            try (InputStream in = process.getInputStream()) {
                String text = decode(in.readAllBytes());
                process.waitFor();
                return text;
            }
        } catch (Exception ex) {
            return "";
        }
    }

    private static String decode(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (utf8.indexOf('\uFFFD') >= 0) {
            return new String(bytes, Charset.forName("windows-1251"));
        }
        return utf8;
    }
}
