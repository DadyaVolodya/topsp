package ru.infereco.demo.barista.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class ScreenFramesTest {

    @Test
    void wrapsRawBase64AsJpegDataUrl() {
        byte[] bytes = new byte[120];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i + 3);
        }
        String raw = Base64.getEncoder().encodeToString(bytes);
        String url = ScreenFrames.dataUrl(raw);
        assertThat(url).startsWith("data:image/jpeg;base64,");
        assertThat(ScreenFrames.decode(url).bytes()).hasSize(120);
    }

    @Test
    void rejectsTinyPayload() {
        assertThatThrownBy(() -> ScreenFrames.decode("YQ=="))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
