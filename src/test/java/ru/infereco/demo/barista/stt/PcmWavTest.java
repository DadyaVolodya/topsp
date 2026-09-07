package ru.infereco.demo.barista.stt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PcmWavTest {

    @Test
    void extractsPcmFromWav() {
        byte[] pcm = {1, 0, 2, 0, 3, 0};
        byte[] wav = new byte[44 + pcm.length];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, wav, 0, 4);
        System.arraycopy("WAVE".getBytes(StandardCharsets.US_ASCII), 0, wav, 8, 4);
        System.arraycopy("fmt ".getBytes(StandardCharsets.US_ASCII), 0, wav, 12, 4);
        ByteBuffer.wrap(wav, 16, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(16);
        System.arraycopy("data".getBytes(StandardCharsets.US_ASCII), 0, wav, 36, 4);
        ByteBuffer.wrap(wav, 40, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(pcm.length);
        System.arraycopy(pcm, 0, wav, 44, pcm.length);

        assertThat(PcmWav.pcm16le(wav)).containsExactly(1, 0, 2, 0, 3, 0);
    }
}
