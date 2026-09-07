package ru.infereco.demo.barista.stt;

public class SttNotReadyException extends RuntimeException {

    public SttNotReadyException(String message) {
        super(message);
    }
}
