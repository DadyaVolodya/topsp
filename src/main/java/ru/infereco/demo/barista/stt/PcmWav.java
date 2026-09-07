package ru.infereco.demo.barista.stt;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class PcmWav {

    private PcmWav() {
    }

    public static byte[] pcm16le(byte[] wavOrPcm) {
        if (wavOrPcm == null || wavOrPcm.length < 12) {
            throw new IllegalArgumentException("empty audio");
        }
        String riff = new String(wavOrPcm, 0, 4, StandardCharsets.US_ASCII);
        if (!"RIFF".equals(riff)) {
            return wavOrPcm;
        }
        int dataOffset = findData(wavOrPcm);
        if (dataOffset < 0 || dataOffset + 8 > wavOrPcm.length) {
            throw new IllegalArgumentException("wav has no data chunk");
        }
        int size = ByteBuffer.wrap(wavOrPcm, dataOffset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        int start = dataOffset + 8;
        int end = Math.min(wavOrPcm.length, start + size);
        byte[] pcm = new byte[end - start];
        System.arraycopy(wavOrPcm, start, pcm, 0, pcm.length);
        if (pcm.length % 2 != 0 && pcm.length > 0) {
            byte[] even = new byte[pcm.length - 1];
            System.arraycopy(pcm, 0, even, 0, even.length);
            return even;
        }
        return pcm;
    }

    private static int findData(byte[] wav) {
        int cursor = 12;
        while (cursor + 8 <= wav.length) {
            String id = new String(wav, cursor, 4, StandardCharsets.US_ASCII);
            int size = ByteBuffer.wrap(wav, cursor + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if ("data".equals(id)) {
                return cursor;
            }
            cursor += 8 + size + (size % 2);
        }
        return -1;
    }
}
