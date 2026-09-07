package ru.infereco.demo.barista.stt;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

@Component
public class SttWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger LOG = LoggerFactory.getLogger(SttWebSocketHandler.class);
    private static final String ATTR = "vosk-stream";

    private final VoskSttService stt;
    private final ObjectMapper mapper = new ObjectMapper();

    public SttWebSocketHandler(VoskSttService stt) {
        this.stt = stt;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!stt.snapshot().ready()) {
            send(session, Map.of("type", "error", "text", "vosk ещё не готов"));
            session.close(CloseStatus.SERVER_ERROR);
            return;
        }
        session.getAttributes().put(ATTR, stt.openStream());
        send(session, Map.of("type", "ready", "engine", "vosk"));
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        VoskStream stream = stream(session);
        if (stream == null) {
            return;
        }
        ByteBuffer buf = message.getPayload();
        byte[] pcm = new byte[buf.remaining()];
        buf.get(pcm);
        emit(session, stream.feed(pcm));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        VoskStream stream = stream(session);
        if (stream == null) {
            return;
        }
        if ("flush".equalsIgnoreCase(message.getPayload().trim())) {
            emit(session, stream.flush());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        closeStream(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        LOG.warn("stt ws error: {}", exception.getMessage());
        closeStream(session);
    }

    private void emit(WebSocketSession session, VoskStream.Event event) throws IOException {
        if (event == null || event.text() == null) {
            return;
        }
        send(session, Map.of("type", event.type(), "text", event.text()));
    }

    private void send(WebSocketSession session, Map<String, String> payload) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
        }
    }

    private static VoskStream stream(WebSocketSession session) {
        Object value = session.getAttributes().get(ATTR);
        return value instanceof VoskStream s ? s : null;
    }

    private static void closeStream(WebSocketSession session) {
        Object value = session.getAttributes().remove(ATTR);
        if (value instanceof VoskStream stream) {
            stream.close();
        }
    }
}
