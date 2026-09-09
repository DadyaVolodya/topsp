package ru.infereco.demo.barista.chat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.infereco.demo.barista.spec.SpecChange;
import ru.infereco.demo.barista.spec.SpecPipeline;
import ru.infereco.demo.barista.metrics.MetricsRegistry;

class ConversationPersistenceTest {
    @TempDir Path root;

    private ChatContext context(String id) {
        return new ChatContext("document", id, "requirements.md", new SpecChange(id, "document", 1, 2,
                "modified", "high", "3.2", "Visits", "Changed", "short old", "short new", "Impact",
                true, "Complete old rule", "Complete new rule", false, List.of(), null));
    }

    @Test void restoresMessagesAndFrozenContextAfterRestart() {
        ConversationStore first = new ConversationStore(root);
        ChatSession a = first.create(context("a"));
        ChatSession b = first.create(context("b"));
        ChatSession general = first.create();
        a.add("user", "Question A", "text", null);
        a.add("assistant", "Answer A", "model", 10L, "http://localhost/visits", null);
        a.setSkill("hint");
        a.rememberScreen("screen");
        b.add("user", "Question B", "text", null);
        general.add("user", "General question", "text", null);

        ConversationStore restored = new ConversationStore(root);
        ChatSession loaded = restored.findContext("document", "a").orElseThrow();
        assertEquals(a.id(), loaded.id());
        assertEquals(a.messages(), loaded.messages());
        assertEquals(context("a"), loaded.context());
        assertEquals("hint", loaded.skill());
        assertEquals("screen", loaded.screenBrief());
        assertEquals(b.id(), restored.findContext("document", "b").orElseThrow().id());
        assertEquals(general.id(), restored.findContext(null, null).orElseThrow().id());
        loaded.add("user", "After restart", "text", null);
        assertEquals(3, new ConversationStore(root).require(a.id()).messages().size());
    }

    @Test void scopedCommandsNeverUsePrimaryDocumentAndOldChatReopens() {
        ConversationStore store = new ConversationStore(root);
        ChatSession session = store.create(context("a"));
        SpecPipeline pipeline = mock(SpecPipeline.class);
        ChatService service = new ChatService(null, store, null, mock(MetricsRegistry.class), null, null,
                null, null, null, pipeline, null, null);
        assertEquals(session.id(), service.open("document", "a").id());
        assertTrue(service.reply(session.id(), "#sp", "text").text().contains("Complete old rule"));
        verifyNoInteractions(pipeline);
        when(pipeline.createChangeTasks("requirements.md", context("a").change())).thenReturn("Created A");
        assertEquals("Created A", service.reply(session.id(), "#task", "text").text());
        verify(pipeline).createChangeTasks("requirements.md", context("a").change());
        verify(pipeline, never()).createFrontBackTasks(any());
        assertThrows(IllegalArgumentException.class, () -> service.open("document", null));
    }
}
