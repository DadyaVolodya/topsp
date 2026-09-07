package ru.infereco.demo.barista.stt;

import org.vosk.Recognizer;

public final class VoskStream implements AutoCloseable {

    private final Recognizer recognizer;
    private final SttLexicon lexicon;
    private String lastPartial = "";

    public VoskStream(Recognizer recognizer, SttLexicon lexicon) {
        this.recognizer = recognizer;
        this.lexicon = lexicon;
        try {
            recognizer.setMaxAlternatives(5);
        } catch (Throwable ignored) {
            // older natives without alternatives
        }
        try {
            recognizer.getClass().getMethod("setWords", boolean.class).invoke(recognizer, true);
        } catch (Throwable ignored) {
            // optional word timestamps
        }
    }

    public synchronized Event feed(byte[] pcm) {
        if (pcm == null || pcm.length < 2) {
            return null;
        }
        int n = pcm.length - (pcm.length % 2);
        boolean done = recognizer.acceptWaveForm(pcm, n);
        if (done) {
            lastPartial = "";
            String picked = lexicon.pick(SttJson.alternatives(recognizer.getResult()));
            if (picked.isBlank()) {
                return null;
            }
            return new Event("final", picked);
        }
        String partial = SttJson.partial(recognizer.getPartialResult());
        if (partial.equals(lastPartial)) {
            return null;
        }
        lastPartial = partial;
        return new Event("partial", partial);
    }

    public synchronized Event flush() {
        String picked = lexicon.pick(SttJson.alternatives(recognizer.getFinalResult()));
        lastPartial = "";
        if (picked.isBlank()) {
            return null;
        }
        return new Event("final", picked);
    }

    @Override
    public synchronized void close() {
        recognizer.close();
    }

    public record Event(String type, String text) {
    }
}
