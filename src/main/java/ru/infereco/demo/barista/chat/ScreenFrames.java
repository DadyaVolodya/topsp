package ru.infereco.demo.barista.chat;

import java.util.Base64;
import java.util.Locale;

public final class ScreenFrames {

    private static final int MIN_BYTES = 80;
    private static final int MAX_BYTES = 1_800_000;

    private ScreenFrames() {
    }

    public static String dataUrl(String image) {
        if (image == null || image.isBlank()) {
            throw new IllegalArgumentException("кадр экрана пуст");
        }
        String raw = image.trim();
        if (raw.startsWith("data:")) {
            int comma = raw.indexOf(',');
            if (comma < 0 || comma == raw.length() - 1) {
                throw new IllegalArgumentException("кадр экрана повреждён");
            }
            Decoded decoded = decode(raw);
            return "data:" + decoded.mime() + ";base64," + Base64.getEncoder().encodeToString(decoded.bytes());
        }
        Decoded decoded = decode(raw);
        return "data:" + decoded.mime() + ";base64," + Base64.getEncoder().encodeToString(decoded.bytes());
    }

    public static Decoded decode(String image) {
        if (image == null || image.isBlank()) {
            throw new IllegalArgumentException("кадр экрана пуст");
        }
        String raw = image.trim();
        String mime = "image/jpeg";
        String payload = raw;
        if (raw.startsWith("data:")) {
            int comma = raw.indexOf(',');
            if (comma < 0) {
                throw new IllegalArgumentException("кадр экрана повреждён");
            }
            String header = raw.substring(5, comma).toLowerCase(Locale.ROOT);
            if (header.startsWith("image/png")) {
                mime = "image/png";
            } else if (header.startsWith("image/webp")) {
                mime = "image/webp";
            } else {
                mime = "image/jpeg";
            }
            payload = raw.substring(comma + 1);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("кадр экрана не base64");
        }
        if (bytes.length < MIN_BYTES || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("кадр экрана слишком тяжёлый или пустой");
        }
        return new Decoded(mime, bytes);
    }

    public record Decoded(String mime, byte[] bytes) {
    }
}
